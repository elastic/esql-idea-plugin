package co.elastic.plugin.autocomplete;

import co.elastic.grammar.EsqlBaseLexer;
import co.elastic.grammar.EsqlBaseParser;
import co.elastic.grammar.EsqlConfig;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;

import java.util.ArrayList;
import java.util.List;


public class Parser {
    public record ParserInfo(EsqlBaseParser parser, List<Token> tokens, Token lastNonSpacetoken) {}
    private static Token getLastNonSpaceToken(List<Token> tokens) {
        for (int i = tokens.size() - 1; i >= 0; i--) {
            Token t = tokens.get(i);
            if (t.getChannel() == Token.DEFAULT_CHANNEL) {
                return t;
            }
        }
        return null;
    }

    public static Parser.ParserInfo parse(String query) {
        EsqlConfig config = new EsqlConfig(false);
        EsqlBaseLexer lexer = new EsqlBaseLexer(CharStreams.fromString(query));
        lexer.removeErrorListeners();
        lexer.setEsqlConfig(config);

        CommonTokenStream tokenStream = new CommonTokenStream(lexer);

        // Fill the token stream (i.e. tokenize the input)
        tokenStream.fill();
        var tokens = new ArrayList<>(tokenStream.getTokens());
        // Remove EOF
        tokens.removeLast();
        var lastToken = getLastNonSpaceToken(tokens);
        EsqlBaseParser parser = new EsqlBaseParser(tokenStream);
        parser.removeErrorListeners();
        parser.setEsqlConfig(config);

        return new ParserInfo(parser, tokens, lastToken);
    }
}
