package com.aresstack.enterpriseai.source.betaview;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Trefferliste von {@code showResult.action}: Spaltenköpfe, Zellwerte und die Öffnen-Aktion je Zeile. Übernommen
 * aus MainframeMate {@code HtmlResultsTableModel} ohne Swing-Tabellenmodell.
 */
final class ResultTable {

    private static final Pattern ACTION_IN_ONCLICK = Pattern.compile("['\"]([^'\"]+\\.action[^'\"]*)['\"]");

    /** Eine Zeile: Spaltenname → Wert (Reihenfolge der Spalten) und die Aktion zum Öffnen des Dokuments. */
    static final class Row {
        final Map<String, String> cells;
        final String action;

        Row(Map<String, String> cells, String action) {
            this.cells = Collections.unmodifiableMap(cells);
            this.action = action;
        }
    }

    private ResultTable() {
    }

    static List<Row> parse(String html) {
        List<Row> rows = new ArrayList<Row>();
        if (html == null || html.trim().isEmpty()) {
            return rows;
        }
        Document doc = Jsoup.parse(html);
        Element table = doc.selectFirst("table");
        if (table == null) {
            return rows;
        }
        List<String> columnNames = new ArrayList<String>();
        Elements headers = table.select("thead th");
        if (headers.isEmpty()) {
            Element firstRow = table.selectFirst("tbody tr");
            if (firstRow != null) {
                for (Element th : firstRow.select("td")) {
                    columnNames.add(th.text().trim());
                }
            }
        } else {
            for (Element th : headers) {
                columnNames.add(th.text().trim());
            }
        }
        for (Element tr : table.select("tbody tr")) {
            Elements cells = tr.select("td");
            if (cells.isEmpty()) {
                continue;
            }
            Map<String, String> row = new LinkedHashMap<String, String>();
            for (int i = 0; i < cells.size() && i < columnNames.size(); i++) {
                Element cell = cells.get(i);
                Element link = cell.selectFirst("a");
                row.put(columnNames.get(i), (link != null ? link.text() : cell.text()).trim());
            }
            String action = null;
            Element link = tr.selectFirst("a");
            if (link != null) {
                String onclick = link.attr("onclick");
                if (onclick != null && !onclick.isEmpty()) {
                    Matcher m = ACTION_IN_ONCLICK.matcher(onclick);
                    if (m.find()) {
                        action = m.group(1);
                    }
                }
                if (action == null && !link.attr("href").isEmpty()) {
                    action = link.attr("href");
                }
                if (action != null) {
                    action = org.jsoup.parser.Parser.unescapeEntities(action, false).trim();
                }
            }
            if (action != null && !action.isEmpty() && !action.startsWith("javascript:")) {
                rows.add(new Row(row, action));
            }
        }
        return rows;
    }
}
