/*
 * Licensed to Elasticsearch B.V. under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch B.V. licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package co.elastic.plugin;

import com.intellij.lang.Language;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;

import java.util.Optional;

public class CommonUtils {

    public static final String ESQL_SEPARATORS = "\"'[ ()=]+";
    public static final String[] METADATA_OPTIONS = new String[]{"_id", "_ignored", "_index", "_index_mode", "_score", "_source", "_version"};
    public static final String[] SOURCE_COMMANDS = new String[]{"FROM", "ROW", "SHOW"};
    public static final String[] PROCESSING_COMMANDS = new String[]{"DISSECT", "DROP", "ENRICH", "EVAL",
        "GROK", "LOOKUP JOIN", "KEEP", "LIMIT", "MV_EXPAND", "RENAME", "SORT", "STATS", "WHERE"};
    public static final String[] FUNCTIONS = new String[]{"ABS",
        "ACOS",
        "ASIN",
        "ATAN",
        "ATAN2",
        "BIT_LENGTH",
        "BUCKET",
        "BYTE_LENGTH",
        "CASE",
        "CATEGORIZE",
        "CBRT",
        "CEIL",
        "CIDR_MATCH",
        "COALESCE",
        "CONCAT",
        "COS",
        "COSH",
        "DATE_DIFF",
        "DATE_EXTRACT",
        "DATE_FORMAT",
        "DATE_PARSE",
        "DATE_TRUNC",
        "E",
        "ENDS_WITH",
        "EXP",
        "FLOOR",
        "FROM_BASE64",
        "GREATEST",
        "HASH",
        "HYPOT",
        "IP_PREFIX",
        "LEAST",
        "LEFT",
        "LENGTH",
        "LOCATE",
        "LOG",
        "LOG10",
        "LTRIM",
        "MATCH",
        "MV_APPEND",
        "MV_AVG",
        "MV_CONCAT",
        "MV_COUNT",
        "MV_DEDUPE",
        "MV_FIRST",
        "MV_LAST",
        "MV_MAX",
        "MV_MEDIAN",
        "MV_MEDIAN_ABSOLUTE_DEVIATION",
        "MV_MIN",
        "MV_PERCENTILE",
        "MV_PSERIES_WEIGHTED_SUM",
        "MV_SLICE",
        "MV_SORT",
        "MV_SUM",
        "MV_ZIP",
        "NOW",
        "PI",
        "POW",
        "QSTR",
        "REPEAT",
        "REPLACE",
        "REVERSE",
        "RIGHT",
        "ROUND",
        "RTRIM",
        "SIGNUM",
        "SIN",
        "SINH",
        "SPACE",
        "SPLIT",
        "SQRT",
        "ST_CONTAINS",
        "ST_DISJOINT",
        "ST_DISTANCE",
        "ST_ENVELOPE",
        "ST_INTERSECTS",
        "ST_WITHIN",
        "ST_X",
        "ST_XMAX",
        "ST_XMIN",
        "ST_Y",
        "ST_YMAX",
        "ST_YMIN",
        "STARTS_WITH",
        "SUBSTRING",
        "TAN",
        "TANH",
        "TAU",
        "TO_BASE64",
        "TO_BOOLEAN",
        "TO_CARTESIANPOINT",
        "TO_CARTESIANSHAPE",
        "TO_DATE_NANOS",
        "TO_DATEPERIOD",
        "TO_DATETIME",
        "TO_DEGREES",
        "TO_DOUBLE",
        "TO_GEOPOINT",
        "TO_GEOSHAPE",
        "TO_INTEGER",
        "TO_IP",
        "TO_LONG",
        "TO_LOWER",
        "TO_RADIANS",
        "TO_STRING",
        "TO_TIMEDURATION",
        "TO_UNSIGNED_LONG",
        "TO_UPPER",
        "TO_VERSION",
        "TRIM",};

    // checking if there's a comment above the text block, and if it's marked with "ES|QL"
    public static boolean checkEsqlCommentAbove(PsiElement element) {
        if (element != null) {

            Project project = element.getProject();
            PsiFile psiFile = element.getContainingFile();
            Document document = PsiDocumentManager.getInstance(project).getDocument(psiFile);

            if (document != null) {
                int offset = element.getTextRange().getStartOffset();
                int line = document.getLineNumber(offset);
                if (line > 0) {
                    int prevLineStart = document.getLineStartOffset(line - 1);
                    // kotlin has a different offset for lines
                    if (element.getLanguage().is(Language.findLanguageByID("kotlin"))) {
                        prevLineStart = document.getLineStartOffset(line - 2);
                    }
                    // get the element at the end of the previous line
                    // -1 to avoid out of bounds
                    int prevLineEnd = document.getLineEndOffset(line - 1);
                    String prevLineText = document.getText(new TextRange(prevLineStart, prevLineEnd));
                    return prevLineText.trim().startsWith("// ES|QL");
                }
            }
        }
        return false;
    }

    public static boolean isKotlinString(PsiElement element) {
        return Optional.ofNullable(element.getParent())
            .map(x -> x.toString().equals("LITERAL_STRING_TEMPLATE_ENTRY"))
            .orElse(false);
    }
}
