package org.iutools.linguisticdata.constraints

import java.io.ByteArrayInputStream
import java.io.InputStream

/*
 * Hand-written replacement for the original JavaCC-generated Imacond parser
 * (Imacond.java/ImacondTokenManager.java/SimpleCharStream.java/Token.java/
 * TokenMgrError.java/ImacondConstants.java). Only the grammar productions and
 * their parse actions were ported — the JavaCC lexer/parser scaffolding was
 * replaced by a small hand-rolled tokenizer + recursive-descent parser that
 * builds the exact same Condition tree.
 *
 * Grammar (from the original generated parser's doc comment):
 *
 * 1. <condMorphMult> ::= <condMorph> ("+" <condMorphMult>)*
 * 2. <condMorph> ::= <condset> (" " <condMorph>)*
 * 3. <condset> ::= <cond> ("," <condset>)*
 * 4. <cond> ::= "!"? ( "(" <condMorph> ")" | <attrvalpair> | <condid> )
 * 5. <attrvalpair> ::= <attr> ":" <val>
 *      "!" = NOT
 * 6. <condid> ::= ["cp" | "cf"] "(" <morphid> ")"
 *
 * cp: condition on preceding morpheme
 * cf: condition on following morpheme
 *
 * Note: the "+"-separated <condMorphMult> and comma/space-separated lists are
 * parsed here as flat lists rather than the original's right-nested recursive
 * structure; And/Or are associative and OverSeveralMorphemes.isMetBy() always
 * returns true regardless of its contents, so this is behaviourally identical.
 */
class Imacond {

    private enum class TokKind { EOF, BAR, COLON, PLUS, NEG, OPENPAR, CLOSEPAR, COMMA, SPACE, CP, CF, IDENT }
    private data class Tok(val kind: TokKind, val image: String)

    private val tokens: List<Tok>
    private var pos: Int = 0

    constructor(str: String) : this(ByteArrayInputStream(str.toByteArray()))

    constructor(stream: InputStream) {
        val text = stream.readBytes().toString(Charsets.UTF_8)
        tokens = tokenize(text)
    }

    @Throws(ParseException::class)
    fun ParseCondition(): Condition {
        val cond = condMorphMult()
        consume(TokKind.EOF)
        return cond
    }

    private fun condMorphMult(): Condition {
        val cs = mutableListOf<Condition>()
        cs.add(condMorph())
        while (peek().kind == TokKind.PLUS) {
            consume(TokKind.PLUS)
            cs.add(condMorph())
        }
        return if (cs.size == 1) cs[0] else Condition.OverSeveralMorphemes(cs)
    }

    private fun condMorph(): Condition {
        val cs = mutableListOf<Condition>()
        cs.add(condset())
        while (peek().kind == TokKind.SPACE) {
            consume(TokKind.SPACE)
            cs.add(condset())
        }
        return if (cs.size == 1) cs[0] else Condition.Or(cs)
    }

    private fun condset(): Condition {
        val cs = mutableListOf<Condition>()
        cs.add(cond())
        while (peek().kind == TokKind.COMMA) {
            consume(TokKind.COMMA)
            cs.add(cond())
        }
        return if (cs.size == 1) cs[0] else Condition.And(cs)
    }

    @Throws(ParseException::class)
    private fun cond(): Condition {
        var neg = false
        if (peek().kind == TokKind.NEG) {
            consume(TokKind.NEG)
            neg = true
        }
        val result: Condition
        when (peek().kind) {
            TokKind.OPENPAR -> {
                consume(TokKind.OPENPAR)
                val inner = condMorph()
                consume(TokKind.CLOSEPAR)
                if (neg) inner.truth = false
                result = inner
            }
            TokKind.IDENT -> {
                val avc = attrValPair()
                if (neg) avc.truth = false
                result = avc
            }
            TokKind.CP, TokKind.CF -> {
                val sc = consume(peek().kind)
                consume(TokKind.OPENPAR)
                val s = morphid()
                consume(TokKind.CLOSEPAR)
                val cid = Condition.Cid(sc.image, s)
                if (neg) cid.truth = false
                result = cid
            }
            else -> throw ParseException("Unexpected token '${peek().image}' while parsing a condition")
        }
        return result
    }

    @Throws(ParseException::class)
    private fun attrValPair(): AttrValCond {
        val t = consume(TokKind.IDENT)
        var res = t.image
        consume(TokKind.COLON)
        val v = value()
        res = "$res:$v"
        return AttrValCond(res)
    }

    @Throws(ParseException::class)
    private fun value(): String {
        var t = consume(TokKind.IDENT)
        var res = t.image
        if (peek().kind == TokKind.BAR) {
            t = consume(TokKind.BAR)
            res += t.image
            t = consume(TokKind.IDENT)
            res += t.image
        }
        return res
    }

    @Throws(ParseException::class)
    private fun morphid(): String {
        consume(TokKind.IDENT)
        consume(TokKind.COLON)
        val t1 = consume(TokKind.IDENT)
        consume(TokKind.BAR)
        val t2 = consume(TokKind.IDENT)
        return "id:${t1.image}/${t2.image}"
    }

    private fun peek(): Tok = tokens[pos]

    @Throws(ParseException::class)
    private fun consume(kind: TokKind): Tok {
        val t = tokens[pos]
        if (t.kind != kind) {
            throw ParseException("Expected $kind but found ${t.kind} ('${t.image}') at token index $pos")
        }
        pos++
        return t
    }

    companion object {
        private fun isIdentChar(c: Char): Boolean {
            return c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || c == '&' || c == '\'' || c == '-'
        }

        private fun tokenize(input: String): List<Tok> {
            val tokens = mutableListOf<Tok>()
            var i = 0
            while (i < input.length) {
                val c = input[i]
                when {
                    c == '\r' || c == '\n' -> i++ // skipped, like the original's jjtoSkip
                    c == ' ' -> { tokens.add(Tok(TokKind.SPACE, " ")); i++ }
                    c == '!' -> { tokens.add(Tok(TokKind.NEG, "!")); i++ }
                    c == '(' -> { tokens.add(Tok(TokKind.OPENPAR, "(")); i++ }
                    c == ')' -> { tokens.add(Tok(TokKind.CLOSEPAR, ")")); i++ }
                    c == '+' -> { tokens.add(Tok(TokKind.PLUS, "+")); i++ }
                    c == ',' -> { tokens.add(Tok(TokKind.COMMA, ",")); i++ }
                    c == '/' -> { tokens.add(Tok(TokKind.BAR, "/")); i++ }
                    c == ':' -> { tokens.add(Tok(TokKind.COLON, ":")); i++ }
                    isIdentChar(c) -> {
                        val start = i
                        while (i < input.length && isIdentChar(input[i])) i++
                        val word = input.substring(start, i)
                        when (word) {
                            "cp" -> tokens.add(Tok(TokKind.CP, word))
                            "cf" -> tokens.add(Tok(TokKind.CF, word))
                            else -> tokens.add(Tok(TokKind.IDENT, word))
                        }
                    }
                    else -> throw ParseException("Unexpected character '$c' at position $i in \"$input\"")
                }
            }
            tokens.add(Tok(TokKind.EOF, ""))
            return tokens
        }
    }
}
