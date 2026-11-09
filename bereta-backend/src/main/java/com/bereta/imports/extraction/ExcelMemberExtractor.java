package com.bereta.imports.extraction;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Excel (.xlsx) : première feuille, mêmes règles d'en-têtes que le CSV. */
@Component
public class ExcelMemberExtractor implements MemberExtractor {

    @Override
    public boolean supports(DetectedFileType type) {
        return type == DetectedFileType.XLSX;
    }

    @Override
    public ImportSourceType sourceType() {
        return ImportSourceType.EXCEL;
    }

    @Override
    public List<ExtractedRow> extract(List<UploadedFile> files) {
        List<ExtractedRow> rows = new ArrayList<>();
        for (UploadedFile file : files) rows.addAll(TabularMemberReader.read(table(file.content())));
        return rows;
    }

    static List<List<String>> table(byte[] content) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(content))) {
            if (workbook.getNumberOfSheets() == 0) return List.of();
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter(Locale.FRANCE);
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            List<List<String>> table = new ArrayList<>();
            for (Row row : sheet) {
                List<String> cells = new ArrayList<>();
                for (int c = 0; c < Math.max(0, row.getLastCellNum()); c++) {
                    Cell cell = row.getCell(c, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                    cells.add(cell == null ? null : formatter.formatCellValue(cell, evaluator));
                }
                table.add(cells);
                if (table.size() > TabularMemberReader.MAX_ROWS + 20) break; // la limite est contrôlée ensuite
            }
            return table;
        } catch (IOException | RuntimeException corrupted) {
            throw ImportRejectedException.unreadable("Fichier Excel illisible ou protégé par mot de passe.");
        }
    }
}
