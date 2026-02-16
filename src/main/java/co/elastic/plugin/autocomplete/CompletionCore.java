package co.elastic.plugin.autocomplete;

import co.elastic.grammar.EsqlBaseParser;
import co.elastic.grammar.completion.CandidatesCollection;
import co.elastic.grammar.completion.CodeCompletionCore;


public class CompletionCore {

    static public CandidatesCollection completions(Parser.ParserInfo parserInfo) {
        var parser = parserInfo.parser();
        var tokens = parserInfo.tokens();

        var codeCompletionCode = CodeCompletionCore.Companion.fromParser(parser);
        var caretIndex = tokens.size() - 1;
        if (caretIndex > 0 && tokens.get(caretIndex).getType() == EsqlBaseParser.EOF) {
            caretIndex--;
        }
        var candidates = codeCompletionCode.collectCandidates(parser.getTokenStream(), caretIndex, null);
        return candidates;
    }

}
