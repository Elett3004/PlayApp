package com.proyecto.PlayApp.service;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Path;
import java.util.zip.ZipFile;
import static org.assertj.core.api.Assertions.assertThat;

class OccupancyWorkbookTest {
    @Test void approvedWorkbookMatchesAllFifteenRuntimeRules() throws Exception {
        var rules = new BeachOccupancyEstimator().rules();
        var factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        try (var workbook = new ZipFile(Path.of("docs/Tabla_porcentajes_ocupacion.xlsx").toFile());
             var input = workbook.getInputStream(workbook.getEntry("xl/worksheets/sheet1.xml"))) {
            var rows = factory.newDocumentBuilder().parse(input).getElementsByTagName("row");
            assertThat(rules).hasSize(15);
            assertThat(rows.getLength()).isEqualTo(rules.size() + 1);
            for (int i = 0; i < rules.size(); i++) {
                var cells = ((Element) rows.item(i + 1)).getElementsByTagName("c");
                var rule = rules.get(i);
                assertThat(cells.item(0).getTextContent()).isEqualTo(rule.condition());
                assertThat(cells.item(1).getTextContent()).isEqualTo(rule.criterion());
                if (rule.minimum() != null) {
                    assertThat((int) Math.round(Double.parseDouble(cells.item(2).getTextContent()) * 100)).isEqualTo(rule.minimum());
                    assertThat((int) Math.round(Double.parseDouble(cells.item(3).getTextContent()) * 100)).isEqualTo(rule.maximum());
                } else {
                    assertThat(cells.item(2).getTextContent()).isEqualTo("Sin estimación");
                }
                assertThat(cells.item(4).getTextContent()).isEqualTo(rule.origin());
            }
        }
    }
}
