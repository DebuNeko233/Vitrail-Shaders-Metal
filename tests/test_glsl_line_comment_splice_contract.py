#!/usr/bin/env python3
"""Behavioral contract for GLSL line comments continued by a backslash-newline."""

from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
LEXER = ROOT / "common/src/main/java/dev/vitrail/glsl/GlslLexer.java"

HARNESS = r'''
package dev.vitrail.glsl;

import java.util.List;

public final class GlslLexerSpliceHarness {
    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static long slashOperators(List<GlslLexer.Token> tokens) {
        return tokens.stream()
                .filter(token -> token.kind() == GlslLexer.Kind.OPERATOR && token.text().equals("/"))
                .count();
    }

    private static GlslLexer.Token firstComment(List<GlslLexer.Token> tokens) {
        return tokens.stream()
                .filter(token -> token.kind() == GlslLexer.Kind.COMMENT)
                .findFirst()
                .orElseThrow(() -> new AssertionError("expected a comment token"));
    }

    public static void main(String[] args) {
        String lf = "// continued \\\n/\nvoid main() {}\n";
        List<GlslLexer.Token> lfTokens = GlslLexer.lex(lf);
        require(slashOperators(lfTokens) == 0, "LF continuation leaked a slash operator");
        require(firstComment(lfTokens).text().equals("// continued \\\n/"),
                "LF continuation was not kept inside the line comment");
        require(GlslLexer.join(lfTokens).equals(lf), "LF source did not round-trip");

        String crlf = "// continued \\\r\n/\r\nvoid main() {}\r\n";
        List<GlslLexer.Token> crlfTokens = GlslLexer.lex(crlf);
        require(slashOperators(crlfTokens) == 0, "CRLF continuation leaked a slash operator");
        require(firstComment(crlfTokens).text().equals("// continued \\\r\n/"),
                "CRLF continuation was not kept inside the line comment");
        require(GlslLexer.join(crlfTokens).equals(crlf), "CRLF source did not round-trip");

        String division = "void main(){ float x = a / b; }\n";
        List<GlslLexer.Token> divisionTokens = GlslLexer.lex(division);
        require(slashOperators(divisionTokens) == 1, "ordinary division must remain an operator");
        require(GlslLexer.join(divisionTokens).equals(division), "division source did not round-trip");
    }
}
'''


def main() -> None:
    with tempfile.TemporaryDirectory(prefix="vitrail-glsl-lexer-") as temp_name:
        temp = Path(temp_name)
        harness = temp / "dev/vitrail/glsl/GlslLexerSpliceHarness.java"
        harness.parent.mkdir(parents=True)
        harness.write_text(HARNESS, encoding="utf-8")

        subprocess.run(
            ["javac", "-d", str(temp), str(LEXER), str(harness)],
            cwd=ROOT,
            check=True,
        )
        subprocess.run(
            ["java", "-cp", str(temp), "dev.vitrail.glsl.GlslLexerSpliceHarness"],
            cwd=ROOT,
            check=True,
        )

    print("glsl line-comment splice contract: PASS")


if __name__ == "__main__":
    main()
