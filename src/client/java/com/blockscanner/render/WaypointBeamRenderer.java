package com.blockscanner.render;

import com.blockscanner.ScanController;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

public final class WaypointBeamRenderer {
    private static final float BEAM_RED = 1.0f;
    private static final float BEAM_GREEN = 0.9f;
    private static final float BEAM_BLUE = 0.2f;
    private static final float BEAM_ALPHA = 1.0f;
    private static final float BEAM_THICKNESS = 8.0f;

    private WaypointBeamRenderer() {
    }

    public static void register(ScanController scanController) {
        WorldRenderEvents.LAST.register(context -> {
            if (scanController == null || !scanController.isActive()) {
                return;
            }

            ChunkPos targetChunk = scanController.getLastTargetChunk();
            if (targetChunk == null) {
                return;
            }

            MinecraftClient client = MinecraftClient.getInstance();
            if (client.world == null) {
                return;
            }

            double startX = targetChunk.getStartX();
            double startZ = targetChunk.getStartZ();
            double centerX = startX + 8.0;
            double centerZ = startZ + 8.0;
            int minY = client.world.getBottomY();
            int maxY = client.world.getTopY();

            MatrixStack matrices = context.matrixStack();
            Camera camera = context.camera();
            Vec3d cameraPos = camera.getPos();

            matrices.push();
            matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

            VertexConsumerProvider consumers = context.consumers();
            VertexConsumer buffer = consumers.getBuffer(RenderLayer.getDebugLineStrip(BEAM_THICKNESS));
            Matrix4f matrix = matrices.peek().getPositionMatrix();

            addBeamLine(buffer, matrix, centerX, centerZ, minY, maxY);

            addBeamLine(buffer, matrix, startX, startZ, minY, maxY);
            addBeamLine(buffer, matrix, startX + 16.0, startZ, minY, maxY);
            addBeamLine(buffer, matrix, startX, startZ + 16.0, minY, maxY);
            addBeamLine(buffer, matrix, startX + 16.0, startZ + 16.0, minY, maxY);

            addBeamLine(buffer, matrix, centerX, startZ, minY, maxY);
            addBeamLine(buffer, matrix, centerX, startZ + 16.0, minY, maxY);
            addBeamLine(buffer, matrix, startX, centerZ, minY, maxY);
            addBeamLine(buffer, matrix, startX + 16.0, centerZ, minY, maxY);

            matrices.pop();
        });
    }

    private static void addBeamLine(VertexConsumer buffer, Matrix4f matrix, double x, double z, int minY, int maxY) {
        buffer.vertex(matrix, (float) x, (float) minY, (float) z)
            .color(BEAM_RED, BEAM_GREEN, BEAM_BLUE, BEAM_ALPHA);
        buffer.vertex(matrix, (float) x, (float) maxY, (float) z)
            .color(BEAM_RED, BEAM_GREEN, BEAM_BLUE, BEAM_ALPHA);
    }
}
