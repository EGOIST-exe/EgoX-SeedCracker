package de.egox.seedcracker;

import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class HudSettingsTest {
    private static final GlassHud.Model MODEL = new GlassHud.Model("STATE", "MODES", "STATS", "PHASE", "VALUE", "DETAIL", .5);
    private static class Capture implements StatusDisplay.Canvas {
        final int width, height; final List<int[]> rectangles = new ArrayList<>(); final List<String> texts = new ArrayList<>();
        Capture(int width, int height) { this.width=width; this.height=height; }
        public int width() { return width; } public int height() { return height; }
        public void fill(int x0,int y0,int x1,int y1,int color) {
            assertTrue(x0>=0 && x1<=width && y0>=0 && y1<=height && x0<=x1 && y0<=y1, Arrays.toString(new int[]{x0,y0,x1,y1}));
            rectangles.add(new int[]{x0,y0,x1,y1,color});
        }
        public void text(String value,int x,int y,int color,int maxWidth) {
            assertTrue(x>=0 && x+maxWidth<=width && y>=0 && y+9<=height); texts.add(value);
        }
    }
    @Test void everyModuleCombinationRemainsVisibleAtAllCornersAndGuiScales() {
        for (int w : new int[]{240,480,960}) for (int h : new int[]{180,240,540}) for (int mask=0;mask<32;mask++) for (int corner=0;corner<4;corner++) {
            var s=new HudSettings();s.header=(mask&1)!=0;s.stats=(mask&2)!=0;s.phase=(mask&4)!=0;s.progress=(mask&8)!=0;s.detail=(mask&16)!=0;
            s.x=(corner&1);s.y=(corner>>1);var canvas=new Capture(w,h);GlassHud.render(canvas,MODEL,s);
            if(mask==0)assertTrue(canvas.rectangles.isEmpty()&&canvas.texts.isEmpty());
            else {assertEquals(s.stats,canvas.texts.contains("STATS"));assertEquals(s.phase,canvas.texts.contains("PHASE"));assertEquals(s.detail,canvas.texts.contains("DETAIL"));}
        }
    }
    @Test void zeroBackgroundRemovesEveryPanelLayerButKeepsTextAndEnabledProgress() {
        var s=new HudSettings();s.background=0;var canvas=new Capture(480,240);GlassHud.render(canvas,MODEL,s);
        assertEquals(3,canvas.rectangles.size());assertTrue(canvas.texts.contains("STATS"));
        s.progress=false;canvas=new Capture(480,240);GlassHud.render(canvas,MODEL,s);assertTrue(canvas.rectangles.isEmpty());
        s.background=100;canvas=new Capture(480,240);GlassHud.render(canvas,MODEL,s);
        assertTrue(canvas.rectangles.stream().anyMatch(r -> (r[4]>>>24)==255));
    }
    @Test void draggingKeepsTheGrabOffsetClampsAndStopsAfterRelease() {
        var s=new HudSettings();var b=GlassHud.bounds(960,540,s);var drag=new HudDragController(s);
        assertFalse(drag.begin(0,0,960,540));assertFalse(drag.move(100,100,960,540));
        assertTrue(drag.begin(b.x()+20,b.y()+15,960,540));assertTrue(drag.move(b.x()+70,b.y()+45,960,540));
        var moved=GlassHud.bounds(960,540,s);assertEquals(b.x()+50,moved.x());assertEquals(b.y()+30,moved.y());
        drag.move(-1000,99999,960,540);assertEquals(0,s.x);assertEquals(1,s.y);
        assertTrue(drag.end());assertFalse(drag.move(100,100,960,540));assertFalse(drag.end());
    }
    @Test void normalizedPlacementAndNudgeSurviveResizingAndReset() {
        var s=new HudSettings();var drag=new HudDragController(s);var b=GlassHud.bounds(960,540,s);
        drag.nudge(20,10,960,540);var moved=GlassHud.bounds(960,540,s);assertEquals(b.x()+20,moved.x());assertEquals(b.y()+10,moved.y());
        var canvas=new Capture(240,180);GlassHud.render(canvas,MODEL,s);
        s.resetPosition();assertEquals(.5,s.x);assertEquals(0,s.y);assertEquals(10,GlassHud.bounds(240,180,s).y());
        GlassHud.render(new Capture(180,240),MODEL,s); // Extremely small windows hide the HUD safely.
    }
    @Test void settingsRoundTripDoesNotResetHiddenModulesOrZeroOpacity() {
        var s=new HudSettings();s.x=.9;s.y=.8;s.background=0;s.header=false;s.progress=false;
        var gson=new com.google.gson.Gson();var loaded=gson.fromJson(gson.toJson(s),HudSettings.class);loaded.validate();
        assertEquals(.9,loaded.x);assertEquals(.8,loaded.y);assertEquals(0,loaded.background);assertFalse(loaded.header);assertFalse(loaded.progress);
        assertTrue(gson.fromJson("{}",HudSettings.class).header);
        loaded.x=Double.NaN;assertThrows(IllegalArgumentException.class,loaded::validate);
        loaded.x=.5;loaded.background=101;assertThrows(IllegalArgumentException.class,loaded::validate);
    }
    @Test void paginationLeavesRoomForNavigationAcrossGuiScales() {
        for(int w:new int[]{240,320,480,960})for(int h:new int[]{180,240,360,540}) {
            var layout=SettingsLayout.of(w,h);
            for(int i=0;i<layout.capacity();i++) {
                assertTrue(layout.x(i)>=0&&layout.x(i)+layout.column()<=w);
                assertTrue(layout.y(i)+20<h-80);
            }
        }
    }
    @Test void screensUseRealNativePointerAndSliderContractsInBothVersions() throws Exception {
        var pointer=net.minecraft.client.input.MouseButtonEvent.class;
        assertNotNull(net.minecraft.client.gui.components.events.GuiEventListener.class.getMethod("mouseClicked",pointer,boolean.class));
        assertNotNull(net.minecraft.client.gui.components.events.GuiEventListener.class.getMethod("mouseDragged",pointer,double.class,double.class));
        assertNotNull(net.minecraft.client.gui.components.AbstractSliderButton.class.getDeclaredMethod("applyValue"));
        try(var input=getClass().getResourceAsStream("/fabric.mod.json")) {
            var metadata=com.google.gson.JsonParser.parseString(new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            assertEquals("de.egox.seedcracker.EgoXModMenu",metadata.getAsJsonObject("entrypoints").getAsJsonArray("modmenu").get(0).getAsString());
            assertFalse(metadata.getAsJsonObject("depends").has("modmenu"));
        }
    }
}
