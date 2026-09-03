import br.com.usinagemmaster.feature.machines.FactorySceneGeometry;

/** Run with javac/java; exercises the actual production projection without an Android SDK. */
public final class FactoryGeometryCheck {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        int cases = 0;
        for (int width : new int[]{280, 320, 360, 384, 420, 600, 840}) {
            for (int height : new int[]{320, 405, 435, 500, 540}) {
                for (float density : new float[]{1f, 1.5f, 2f, 3f, 4f}) {
                    for (int rows = 3; rows <= 6; rows++) {
                        float w = width * density, h = height * density;
                        FactorySceneGeometry g = new FactorySceneGeometry(w, h, rows);
                        float scale = g.machineScale;
                        for (int y = 0; y < rows; y++) for (int x = 0; x < 5; x++) {
                            float cx = g.machineX(x), cy = g.machineY(y);
                            check(cx - 45 * scale >= 0 && cx + 45 * scale <= w, "Machine outside horizontal viewport");
                            check(cy - 48 * scale >= 0 && cy + 36 * scale <= h, "Machine outside vertical viewport");
                            check(g.hitsMachine(cx, cy, x, y), "Machine cannot be selected at its center");
                            for (int by = 0; by < rows; by++) for (int bx = 0; bx < 5; bx++) {
                                if (x == bx && y == by) continue;
                                float dx = Math.abs(cx - g.machineX(bx)), dy = Math.abs(cy - g.machineY(by));
                                check(dx >= 90 * scale || dy >= 84 * scale, "Machine artwork bounds overlap");
                            }
                            for (float zoom : new float[]{.78f, 1f, 2.1f, 3.25f}) {
                                float panX = w * .13f, panY = -h * .07f;
                                float screenX = w / 2f + (cx - w / 2f) * zoom + panX;
                                float screenY = h / 2f + (cy - h / 2f) * zoom + panY;
                                float originalX = g.unproject(screenX, w / 2f, zoom, panX);
                                float originalY = g.unproject(screenY, h / 2f, zoom, panY);
                                check(Math.abs(originalX - cx) < .01f && Math.abs(originalY - cy) < .01f, "Camera inverse is inconsistent");
                                check(g.hitsMachine(originalX, originalY, x, y), "Zoom/pan prevents selection");
                            }
                        }
                        for (int i = 1; i <= 96; i++) {
                            check(g.y(i / 4f) > g.y((i - 1) / 4f), "World rows collapse or reverse");
                        }
                        for (int i = 0; i <= 20; i++) {
                            check(g.x(i) >= 0 && g.x(i) <= w && g.y(24) <= h, "Service station leaves viewport");
                        }
                        FactorySceneGeometry baseline = new FactorySceneGeometry(width, height, rows);
                        check(Math.abs(g.machineScale / density - baseline.machineScale) < .0001f, "Density changes relative scale");
                        cases++;
                    }
                }
            }
        }
        check(new FactorySceneGeometry(360, 405, 1).rows == 3, "Minimum number of rows");
        check(new FactorySceneGeometry(360, 405, 20).rows == 6, "Maximum number of rows");
        System.out.println("PASS: " + cases + " viewports; artwork bounds, density, projection and zoom/pan hit testing.");
    }
}
