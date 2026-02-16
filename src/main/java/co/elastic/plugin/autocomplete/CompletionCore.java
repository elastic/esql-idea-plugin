package co.elastic.plugin.autocomplete;

import co.elastic.grammar.EsqlBaseLexer;
import co.elastic.grammar.EsqlBaseParser;
import co.elastic.grammar.EsqlConfig;
import co.elastic.grammar.completion.CandidatesCollection;
import co.elastic.grammar.completion.CodeCompletionCore;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;

import java.util.List;


public class CompletionCore {
    private record ParserInfo(EsqlBaseParser parser, List<Token> tokens){}

    private static ParserInfo getEsqlParser(String query) {
        EsqlConfig config = new EsqlConfig(false);
        EsqlBaseLexer lexer = new EsqlBaseLexer(CharStreams.fromString(query));
        lexer.removeErrorListeners();
        lexer.setEsqlConfig(config);

        CommonTokenStream tokenStream = new CommonTokenStream(lexer);

        // Get all tokens
        tokenStream.fill();
        var tokens = tokenStream.getTokens();
        EsqlBaseParser parser = new EsqlBaseParser(tokenStream);
        parser.removeErrorListeners();
        parser.setEsqlConfig(config);

        return new ParserInfo(parser, tokens);
    }

    static public CandidatesCollection completions(String query) {
        var parserInfo = getEsqlParser(query);
        var parser = parserInfo.parser;
        var tokens = parserInfo.tokens;

        var codeCompletionCode = CodeCompletionCore.Companion.fromParser(parser);
        var caretIndex = tokens.size() - 1;
        var candidates = codeCompletionCode.collectCandidates(parser.getTokenStream(), caretIndex, null);
        return candidates;
    }

}
