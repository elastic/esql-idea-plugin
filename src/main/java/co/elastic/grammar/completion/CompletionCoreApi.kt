/*
 This file is released under the MIT license.
 Copyright (c) 2016-2017, Mike Lischke, Federico Tomassetti

 MIT License

 Copyright (c) 2016, 2017, Mike Lischke

 Permission is hereby granted, free of charge, to any person obtaining a copy
 of this software and associated documentation files (the "Software"), to deal
 in the Software without restriction, including without limitation the rights
 to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 copies of the Software, and to permit persons to whom the Software is
 furnished to do so, subject to the following conditions:

 The above copyright notice and this permission notice shall be included in all
 copies or substantial portions of the Software.

 THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 SOFTWARE.

 Copied from https://github.com/ftomassetti/antlr4-c3-kotlin/blob/master/src/main/kotlin/me/tomassetti/antlr4c3/api/api.kt
 */

package co.elastic.grammar.completion

import org.antlr.v4.runtime.*
import java.io.ByteArrayInputStream
import java.nio.charset.Charset

fun <L : Lexer, P : Parser> completionsWithContext(
    code: String,
    lexerClass: Class<L>,
    parserClass: Class<P>
): CandidatesCollection {
    val lexerConstructor =
        lexerClass.constructors.find { it.parameterCount == 1 && it.parameterTypes[0] == CharStream::class.java }!!
    val charStream = ANTLRInputStream(ByteArrayInputStream(code.toByteArray(Charset.defaultCharset())))
    val lexer = lexerConstructor.newInstance(charStream) as Lexer

    val parserConstructor =
        parserClass.constructors.find { it.parameterCount == 1 && it.parameterTypes[0] == TokenStream::class.java }!!
    val parser = parserConstructor.newInstance(CommonTokenStream(lexer)) as Parser
    val codeCompletionCode = CodeCompletionCore.fromParser(parser)

    return codeCompletionCode.collectCandidates(parser.tokenStream, code.length)
}

fun <L : Lexer, P : Parser> completions(code: String, lexerClass: Class<L>, parserClass: Class<P>): Set<TokenKind> {
    return completionsWithContext(code, lexerClass, parserClass).tokens.keys.toSet()
}

