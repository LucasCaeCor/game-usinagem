package br.com.usinagemmaster.feature.machines;

/** Portrait floor projection shared by drawing and hit testing; independent of Android. */
public final class FactorySceneGeometry {
    public final int rows;
    public final float left, right, top, bottom, serviceBottom;
    public final float cellWidth, cellHeight, machineScale, workerScale;

    public FactorySceneGeometry(float width, float height, int occupiedRows) {
        if (!Float.isFinite(width) || !Float.isFinite(height) || width <= 0 || height <= 0) {
            throw new IllegalArgumentException("The viewport must have a finite positive size");
        }
        rows = Math.max(3, Math.min(6, occupiedRows));
        left = width * .065f;
        right = width * .935f;
        top = height * .16f;
        bottom = height * .81f;
        serviceBottom = height * .94f;
        cellWidth = (right - left) / 5f;
        cellHeight = (bottom - top) / rows;
        // Largest machine artwork fits inside a bay at every density, without a minimum scale.
        machineScale = Math.min(cellWidth / 105f, cellHeight / 90f);
        workerScale = machineScale * .64f;
    }

    public float x(float worldX) {
        return left + Math.max(0f, Math.min(20f, worldX)) * (right - left) / 20f;
    }

    public float y(float worldY) {
        float value = Math.max(0f, Math.min(24f, worldY));
        float activeEnd = rows * 4f;
        if (value <= activeEnd) return top + value * cellHeight / 4f;
        // Unoccupied rows remain traversable, represented by the service strip below the bays.
        return bottom + (value - activeEnd) / (24f - activeEnd) * (serviceBottom - bottom);
    }

    public float machineX(int gridX) { return x(Math.max(0, Math.min(4, gridX)) * 4f + 2f); }
    public float machineY(int gridY) { return y(Math.max(0, Math.min(5, gridY)) * 4f + 2f); }

    public boolean hitsMachine(float px, float py, int gridX, int gridY) {
        float cx = machineX(gridX), cy = machineY(gridY);
        return px >= cx - 45f * machineScale && px <= cx + 45f * machineScale
            && py >= cy - 48f * machineScale && py <= cy + 36f * machineScale;
    }

    public float unproject(float screen, float center, float zoom, float pan) {
        if (!Float.isFinite(zoom) || zoom <= 0f) throw new IllegalArgumentException("Invalid zoom");
        return center + (screen - center - pan) / zoom;
    }
}
