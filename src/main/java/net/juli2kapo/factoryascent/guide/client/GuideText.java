package net.juli2kapo.factoryascent.guide.client;

import java.util.ArrayList;
import java.util.List;
import net.juli2kapo.factoryascent.guide.GuideEntries;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * Lays out the Manual's markup into positioned words. Markup (in the lang files):
 * <ul>
 *   <li>a line break starts a new paragraph; {@code "# "} starts a heading, {@code "- "} a bullet;</li>
 *   <li>{@code [[item_id]]} or {@code [[item_id|label]]}: a link to an item (its name by default);</li>
 *   <li>{@code [[@entry_id]]} or {@code [[@entry_id|label]]}: a link to an entry;</li>
 *   <li>{@code **bold**}.</li>
 * </ul>
 */
public final class GuideText {
    public static final int LINE_H = 10;

    /** A link target: an item or an entry. */
    public record Link(@Nullable Item item, @Nullable String entry) {}

    /** A word placed at (x, y) relative to the text box. */
    public record Word(String text, Style style, int x, int y, int width, @Nullable Link link, int color) {}

    public record Laid(List<Word> words, int height) {}

    private record Token(String text, Style style, @Nullable Link link, int color) {}

    private GuideText() {}

    public static Laid layout(Font font, String markup, int width, int textColor, int linkColor, int headColor) {
        List<Word> words = new ArrayList<>();
        int y = 0;
        for (String raw : markup.split("\n")) {
            String para = raw.strip();
            if (para.isEmpty()) {
                y += LINE_H / 2;
                continue;
            }
            int indent = 0;
            Style base = Style.EMPTY;
            int color = textColor;
            if (para.startsWith("# ")) {
                para = para.substring(2);
                base = base.withBold(true);
                color = headColor;
                y += 2;
            } else if (para.startsWith("- ")) {
                para = para.substring(2);
                words.add(new Word("•", Style.EMPTY, 1, y, font.width("•"), null, headColor));
                indent = 8;
            }
            List<Token> tokens = tokenize(para, base, color, linkColor);
            int x = indent;
            for (Token t : tokens) {
                for (String w : splitKeepingSpaces(t.text)) {
                    int ww = font.width(Component.literal(w).withStyle(t.style));
                    boolean space = w.isBlank();
                    if (x + ww > width && x > indent && !space) {
                        x = indent;
                        y += LINE_H;
                    }
                    if (space && x == indent) continue;
                    if (!space) words.add(new Word(w, t.style, x, y, ww, t.link, t.color));
                    x += ww;
                }
            }
            y += LINE_H + 2;
        }
        return new Laid(words, y);
    }

    private static List<String> splitKeepingSpaces(String s) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (c == ' ') {
                if (!cur.isEmpty()) out.add(cur.toString());
                cur.setLength(0);
                out.add(" ");
            } else {
                cur.append(c);
            }
        }
        if (!cur.isEmpty()) out.add(cur.toString());
        return out;
    }

    private static List<Token> tokenize(String para, Style base, int color, int linkColor) {
        List<Token> out = new ArrayList<>();
        int i = 0;
        boolean bold = false;
        StringBuilder plain = new StringBuilder();
        while (i < para.length()) {
            if (para.startsWith("**", i)) {
                flush(out, plain, bold ? base.withBold(true) : base, color);
                bold = !bold;
                i += 2;
                continue;
            }
            if (para.startsWith("[[", i)) {
                int end = para.indexOf("]]", i);
                if (end > 0) {
                    flush(out, plain, bold ? base.withBold(true) : base, color);
                    String body = para.substring(i + 2, end);
                    String target = body, label = null;
                    int bar = body.indexOf('|');
                    if (bar >= 0) {
                        target = body.substring(0, bar);
                        label = body.substring(bar + 1);
                    }
                    Link link;
                    if (target.startsWith("@")) {
                        String id = target.substring(1);
                        link = new Link(null, id);
                        if (label == null) {
                            GuideEntries.Entry e = GuideEntries.get(id);
                            label = e == null ? id : Component.translatable(e.titleKey()).getString();
                        }
                    } else {
                        Item item = GuideEntries.item(target);
                        link = item == Items.AIR ? null : new Link(item, null);
                        if (label == null) label = item == Items.AIR ? target : new net.minecraft.world.item.ItemStack(item).getHoverName().getString();
                    }
                    Style ls = (bold ? base.withBold(true) : base).withUnderlined(link != null);
                    out.add(new Token(label, ls, link, link != null ? linkColor : color));
                    i = end + 2;
                    continue;
                }
            }
            plain.append(para.charAt(i));
            i++;
        }
        flush(out, plain, bold ? base.withBold(true) : base, color);
        return out;
    }

    private static void flush(List<Token> out, StringBuilder plain, Style style, int color) {
        if (plain.isEmpty()) return;
        out.add(new Token(plain.toString(), style, null, color));
        plain.setLength(0);
    }
}
