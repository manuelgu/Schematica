package com.github.lunatrius.schematica.handler.client;

import com.github.lunatrius.schematica.SchematicPrinter;
import com.github.lunatrius.schematica.Schematica;
import com.github.lunatrius.schematica.client.renderer.RendererSchematicChunk;
import com.github.lunatrius.schematica.client.renderer.RendererSchematicGlobal;
import com.github.lunatrius.schematica.handler.ConfigurationHandler;
import com.github.lunatrius.schematica.proxy.ClientProxy;
import com.github.lunatrius.schematica.reference.Reference;
import com.github.lunatrius.schematica.world.SchematicWorld;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.util.AxisAlignedBB;

public class TickHandler {
    public static final TickHandler INSTANCE = new TickHandler();

    private final Minecraft minecraft = Minecraft.getMinecraft();

    private int ticks = -1;

    private TickHandler() {}

    @SubscribeEvent
    public void onClientConnect(FMLNetworkEvent.ClientConnectedToServerEvent event) {
        Reference.logger.info("Scheduling client settings reset.");
        ClientProxy.isPendingReset = true;
    }

    @SubscribeEvent
    public void onClientDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        Reference.logger.info("Scheduling client settings reset.");
        ClientProxy.isPendingReset = true;
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            this.minecraft.mcProfiler.startSection("schematica");
            SchematicWorld schematic = Schematica.proxy.getActiveSchematic();
            if (this.minecraft.thePlayer != null && schematic != null && schematic.isRendering) {
                this.minecraft.mcProfiler.startSection("printer");
                SchematicPrinter printer = SchematicPrinter.INSTANCE;
                if (printer.isEnabled() && printer.isPrinting() && this.ticks-- < 0) {
                    this.ticks = ConfigurationHandler.placeDelay;

                    printer.print();
                }

                this.minecraft.mcProfiler.endStartSection("checkDirty");
                checkDirty(schematic);

                this.minecraft.mcProfiler.endStartSection("canUpdate");
                RendererSchematicChunk.setCanUpdate(true);

                this.minecraft.mcProfiler.endSection();
            }

            if (ClientProxy.isPendingReset) {
                Schematica.proxy.resetSettings();
                ClientProxy.isPendingReset = false;
            }

            this.minecraft.mcProfiler.endSection();
        }
    }

    private void checkDirty(SchematicWorld schematic) {
        WorldRenderer[] renderers = this.minecraft.renderGlobal.sortedWorldRenderers;
        if (renderers != null) {
            int count = 0;
            for (WorldRenderer worldRenderer : renderers) {
                if (worldRenderer != null && worldRenderer.needsUpdate && count++ < 125) {
                    AxisAlignedBB worldRendererBoundingBox = worldRenderer.rendererBoundingBox.getOffsetBoundingBox(-schematic.position.x, -schematic.position.y, -schematic.position.z);
                    for (RendererSchematicChunk renderer : RendererSchematicGlobal.INSTANCE.sortedRendererSchematicChunk) {
                        if (!renderer.getDirty() && renderer.getBoundingBox().intersectsWith(worldRendererBoundingBox)) {
                            renderer.setDirty();
                        }
                    }
                }
            }
        }
    }
}
