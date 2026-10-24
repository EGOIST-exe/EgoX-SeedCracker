package de.egox.seedcracker;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.function.*;

/** Native widgets provide focus traversal, narration, slider keys and tooltips. */
public abstract class EgoXSettingsScreen extends Screen {
    private static final String[] TABS = {"HUD", "Module", "Outlines", "Leistung"};
    private final Screen parent;
    protected final boolean editor;
    private int tab, page;
    private HudDragController drag;
    private List<Control> controls = List.of();
    private record Control(String name, String help, BooleanSupplier get, Consumer<Boolean> set, IntSupplier number, IntConsumer change, int min, int max, Runnable action) { }
    protected EgoXSettingsScreen(Screen parent, boolean editor) {
        super(Component.literal(editor ? "EgoX HUD-Position" : "EgoX Einstellungen")); this.parent = parent; this.editor = editor;
    }
    @Override protected void init() {
        toggles.clear();
        var config = EgoXConfig.get();
        if (editor) { initEditor(); return; }
        var layout = SettingsLayout.of(width, height);
        for (int i = 0; i < TABS.length; i++) {
            int selected = i, size = (layout.width() - 12) / 4;
            var button = button(TABS[i], layout.left() + i * (size + 4), 42, size, () -> { tab = selected; page = 0; rebuildWidgets(); });
            button.active = tab != i;
        }
        controls = switch (tab) { case 0 -> hudControls(config); case 1 -> modules(config); case 2 -> outlines(config); default -> performance(config); };
        int pages = Math.max(1, (controls.size() + layout.capacity() - 1) / layout.capacity()); page = Math.min(page, pages - 1);
        for (int i = 0; i < layout.capacity() && page * layout.capacity() + i < controls.size(); i++) {
            var control = controls.get(page * layout.capacity() + i); int x = layout.x(i), y = layout.y(i);
            if (control.number() != null) {
                var slider = new ValueSlider(x, y, layout.column(), control); slider.setTooltip(Tooltip.create(Component.literal(control.help()))); addRenderableWidget(slider);
            } else {
                var widget = button(label(control), x, y, layout.column(), () -> { if (control.action() != null) control.action().run(); else control.set().accept(!control.get().getAsBoolean()); });
                if (control.action() == null) widget = refreshToggle(widget, control);
                widget.setTooltip(Tooltip.create(Component.literal(control.help())));
            }
        }
        button("Zurück", layout.left(), height - 54, 70, () -> { page--; rebuildWidgets(); }).active = page > 0;
        button("Weiter", layout.left() + layout.width() - 70, height - 54, 70, () -> { page++; rebuildWidgets(); }).active = page + 1 < pages;
        button("Fertig", width / 2 - 60, height - 28, 120, this::onClose);
    }
    private Button refreshToggle(Button previous, Control control) {
        // Update the label in place: changing a switch preserves keyboard focus.
        previous.setMessage(Component.literal(label(control)));
        toggles.put(previous, control);
        return previous;
    }
    private String label(Control control) { return control.action() != null ? control.name() : control.name() + ": " + (control.get().getAsBoolean() ? "ON" : "OFF"); }
    private Button button(String label, int x, int y, int width, Runnable action) {
        var result = Button.builder(Component.literal(label), b -> { action.run(); refreshLabels(); }).bounds(x, y, width, 20).build();
        return addRenderableWidget(result);
    }
    private final Map<Button, Control> toggles = new IdentityHashMap<>();
    private void refreshLabels() { toggles.forEach((button, control) -> button.setMessage(Component.literal(label(control)))); }
    @Override public void removed() { if (drag != null) drag.end(); EgoXConfig.save(); super.removed(); }
    @Override public void onClose() { EgoXConfig.save(); SettingsPlatform.show(parent); }
    @Override public boolean isPauseScreen() { return false; }
    public boolean editingHud() { return editor; }
    private Control toggle(String name, String help, BooleanSupplier get, Consumer<Boolean> set) { return new Control(name, help, get, set, null, null, 0, 0, null); }
    private Control action(String name, String help, Runnable action) { return new Control(name, help, null, null, null, null, 0, 0, action); }
    private Control number(String name, String help, int min, int max, IntSupplier get, IntConsumer set) { return new Control(name, help, null, null, get, set, min, max, null); }
    private List<Control> hudControls(EgoXConfig c) {
        var s = c.hudStyle;
        return List.of(toggle("HUD", "HUD im Spiel anzeigen. Die Vorschau im Editor bleibt auch bei OFF sichtbar.", () -> c.hud, v -> c.hud = v),
            action("HUD verschieben", "Ziehen mit der Maus oder mit den Bewegungsbuttons verschieben. Position bleibt nach GUI-Skalierung sichtbar.", () -> SettingsPlatform.show(new SettingsPlatform(this, true))),
            number("Hintergrund", "0%: vollständig transparent, einschließlich Rahmen, Glanz und Schatten. Text und aktivierte Balken bleiben sichtbar.", 0, 100, () -> s.background, v -> s.background = v),
            toggle("Kopfzeile", "EGOX, Zustand und Seed-/Salt-Schalter im HUD.", () -> s.header, v -> s.header = v),
            toggle("Datenzeile", "Strukturstarts, theoretische Bits und Biom-Anzahl.", () -> s.stats, v -> s.stats = v),
            toggle("Suchphase", "Aktuelle Phase und Fortschrittswert.", () -> s.phase, v -> s.phase = v),
            toggle("Fortschrittsbalken", "Balken anzeigen. Beim Sammeln ein Datenziel; während einer Suche der Phasenfortschritt.", () -> s.progress, v -> s.progress = v),
            toggle("Detailzeile", "Geschwindigkeit, Restzeit oder Diagnosehinweis.", () -> s.detail, v -> s.detail = v),
            action("HUD-Look zurücksetzen", "Setzt nur Position, Hintergrund und HUD-Zeilen zurück. Der HUD-An/Aus-Schalter bleibt erhalten.", () -> { c.hudStyle = new HudSettings(); rebuildWidgets(); }));
    }
    private List<Control> modules(EgoXConfig c) {
        return List.of(toggle("Seed-Cracker", "Automatische Seed-Suche. OFF fordert einen Abbruch an; Beobachtungen bleiben erhalten.", () -> c.autoCrack, CrackerControl::seed),
            toggle("Salt-Cracker", "Automatische Suche nach Struktur-Salts.", () -> c.autoSaltCrack, CrackerControl::salt),
            toggle("Biom-Checker", "Kandidaten mit nativen Overworld-/Nether-Biomen prüfen. Standardmäßig OFF; Sammlung ist ein eigener Schalter.", () -> c.autoBiomes, CrackerControl::biomes),
            toggle("Erz-Checker", "Optionale Smaragderz-Kandidatenprüfung. Standardmäßig OFF; Erz-Journal bleibt aktiv.", () -> c.autoOres, CrackerControl::ore),
            toggle("Bedrock-Checker", "Kandidaten anhand beobachteter Vanilla-Übergangsschichten prüfen.", () -> c.bedrockChecker, CrackerControl::bedrock),
            toggle("Geoden-Checker", "Drusen-Platzierungsbedingungen prüfen; unabhängig von Geoden-Outlines.", () -> c.geodeChecker, CrackerControl::geode),
            toggle("Dungeon-Checker", "Intakte Dungeon-Bodenmuster zur Kandidatenprüfung verwenden.", () -> c.dungeonChecker, CrackerControl::dungeon),
            toggle("Struktur-Finder", "Geladene Chunks auf Strukturen prüfen. Neue Scans starten beim Einschalten.", () -> c.finder, v -> { c.finder = v; if (v) StructureScanner.rescan(); }),
            toggle("Biom-Sammlung", "Neue Biom-Proben lokal erfassen. Bereits gespeicherte Daten bleiben erhalten.", () -> c.collectBiomes, v -> c.collectBiomes = v),
            toggle("Zusatzfunde", "Spawner, Vaults und Drusen erfassen und markieren.", () -> c.specialBlocks, v -> { c.specialBlocks = v; if (v) StructureScanner.rescan(); }),
            toggle("Seed-DB-Upload", "Hash-bestätigte Multiplayer-Seeds automatisch an die Community-Tabelle senden. Rohdaten bleiben lokal.", () -> c.autoUpload, v -> c.autoUpload = v));
    }
    private List<Control> outlines(EgoXConfig c) {
        var list = new ArrayList<Control>();
        list.add(toggle("Alle Outlines", "Globaler Sichtbarkeitsschalter. Einzelne Typauswahl bleibt erhalten.", () -> c.outlines, v -> c.outlines = v));
        list.add(toggle("Beschriftungen", "Text an Outline-Markierungen anzeigen.", () -> c.outlineLabels, v -> c.outlineLabels = v));
        for (String type : OutlineVisibility.types()) list.add(toggle(type.substring(type.indexOf(':') + 1), type + ": nur Darstellung umschalten; Daten bleiben gespeichert.",
            () -> OutlineVisibility.enabled(c.disabledOutlineTypes, type), v -> { OutlineVisibility.set(c.disabledOutlineTypes, type, v); if (v && type.equals(OutlineVisibility.GEODE)) StructureScanner.rescan(); }));
        return list;
    }
    private List<Control> performance(EgoXConfig c) {
        int cores = Runtime.getRuntime().availableProcessors();
        return List.of(number("Such-Threads", "1–64 Worker für neue Suchjobs und Phasen. Mehr Threads sind nicht automatisch schneller. Bereits gestartete Worker werden nicht umgestellt.", 1, 64, () -> c.workers, v -> c.workers = v),
            action("Empfohlen", "Bis zu 16 Threads, zwei logische Prozessoren bleiben rechnerisch frei. Wirkt ab der nächsten Suche.", () -> { c.workers = Math.max(1, Math.min(16, cores - 2)); rebuildWidgets(); }),
            action("Sparsam: 1 Thread", "Reduzierte parallele Suchlast ab der nächsten Suche.", () -> { c.workers = 1; rebuildWidgets(); }),
            action("Ausgewogen", "Die Hälfte der erkannten logischen Prozessoren, maximal 64.", () -> { c.workers = Math.max(1, Math.min(64, cores / 2)); rebuildWidgets(); }),
            action("Maximale Parallelität", "Ein Worker je logischem Prozessor, maximal 64. Kann FPS und Arbeitsspeicher beeinflussen.", () -> { c.workers = Math.min(64, cores); rebuildWidgets(); }));
    }
    private void initEditor() {
        var style = EgoXConfig.get().hudStyle; drag = new HudDragController(style);
        int size = Math.min(84, (width - 28) / 4), start = (width - (size * 4 + 12)) / 2;
        String[] names = {"Links", "Rechts", "Hoch", "Runter"}; int[][] delta = {{-2,0},{2,0},{0,-2},{0,2}};
        for (int i=0;i<4;i++) { int index=i; button(names[i], start+i*(size+4), height-54, size, () -> drag.nudge(delta[index][0], delta[index][1], width, height)).active = style.any(); }
        button("Oben mittig", width/2-104, height-28, 100, () -> { style.resetPosition(); drag.end(); });
        button("Fertig", width/2+4, height-28, 100, this::onClose);
    }
    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        return editor && event.button()==0 && drag.begin(event.x(), event.y(), width, height);
    }
    @Override public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        return editor && event.button()==0 && drag.move(event.x(), event.y(), width, height) || super.mouseDragged(event, dx, dy);
    }
    @Override public boolean mouseReleased(MouseButtonEvent event) {
        if (editor && event.button()==0 && drag.end()) return true;
        return super.mouseReleased(event);
    }
    protected void drawContents(StatusDisplay.Canvas canvas) {
        if (editor) {
            GlassHud.render(canvas, StatusDisplay.preview(), EgoXConfig.get().hudStyle);
            canvas.fill(0,height-82,width,height,0xE0172431);
            canvas.text(EgoXConfig.get().hudStyle.any() ? "HUD ziehen oder mit den Buttons verschieben. Esc speichert." : "Alle HUD-Zeilen sind aus. Aktiviere sie im HUD-Menü.", 12, height-76, 0xFFF1F6FC, width-24);
            return;
        }
        var layout = SettingsLayout.of(width, height);
        canvas.fill(layout.left()-8, 6, layout.left()+layout.width()+8, height-6, 0xED172431);
        canvas.fill(layout.left()-8, 6, layout.left()+layout.width()+8, 7, 0xFFA9F0E3);
        canvas.text("EGOX | Einstellungen", layout.left(), 14, 0xFFA9F0E3, layout.width());
        canvas.text("HUD, Module und Rechenleistung", layout.left(), 27, 0xFFE2EAF2, layout.width());
        String hint = switch (tab) { case 0 -> "HUD-Vorschau ist auch bei ausgeschaltetem HUD verfügbar."; case 1 -> "ON/OFF wirkt auf das Modul; gespeicherte Daten bleiben erhalten."; case 2 -> "Jeder Typ hat einen eigenen Sichtbarkeitsschalter."; default -> Runtime.getRuntime().availableProcessors()+" logische Prozessoren | Änderungen ab nächster Suche"; };
        canvas.text(hint, layout.left(), height-80, 0xFFE2EAF2, layout.width());
        canvas.text(tab==3 ? "Biom-/Feature-Fallbacks nutzen höchstens zwei Such-Threads." : "Änderungen werden beim Verlassen gespeichert.", layout.left(), height-69, 0xFFB5C7D8, layout.width());
        int pages = Math.max(1, (controls.size()+layout.capacity()-1)/layout.capacity());
        canvas.text((page+1)+" / "+pages, width/2-14, height-48, 0xFFE2EAF2, 40);
    }
    private final class ValueSlider extends AbstractSliderButton {
        private final Control control;
        ValueSlider(int x, int y, int width, Control control) {
            super(x,y,width,20,Component.empty(),(control.number().getAsInt()-control.min())/(double)(control.max()-control.min()));
            this.control=control; updateMessage();
        }
        @Override protected void updateMessage() { setMessage(Component.literal(control.name()+": "+control.number().getAsInt()+(control.name().equals("Hintergrund")?"%":""))); }
        @Override protected void applyValue() { control.change().accept(control.min()+(int)Math.round(value*(control.max()-control.min()))); }
    }
}
