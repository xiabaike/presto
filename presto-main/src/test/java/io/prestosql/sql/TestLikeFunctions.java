/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.prestosql.sql;

import io.airlift.slice.Slice;
import io.airlift.slice.Slices;
import io.prestosql.operator.scalar.AbstractTestFunctions;
import io.prestosql.spi.PrestoException;
import io.prestosql.type.JoniRegexp;
import io.prestosql.type.LikeFunctions;
import org.testng.annotations.Test;

import java.util.Optional;

import static io.airlift.slice.Slices.utf8Slice;
import static io.prestosql.spi.type.BooleanType.BOOLEAN;
import static io.prestosql.type.LikeFunctions.isLikePattern;
import static io.prestosql.type.LikeFunctions.likeChar;
import static io.prestosql.type.LikeFunctions.likePattern;
import static io.prestosql.type.LikeFunctions.likeVarchar;
import static io.prestosql.type.LikeFunctions.patternConstantPrefixBytes;
import static io.prestosql.type.LikeFunctions.unescapeLiteralLikePattern;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class TestLikeFunctions
        extends AbstractTestFunctions
{
    private static Slice offsetHeapSlice(String value)
    {
        Slice source = Slices.utf8Slice(value);
        Slice result = Slices.allocate(source.length() + 5);
        result.setBytes(2, source);
        return result.slice(2, source.length());
    }

    @Test
    public void testLikeBasic()
    {
        JoniRegexp regex = LikeFunctions.compileLikePattern(utf8Slice("f%b__"));
        assertTrue(likeVarchar(utf8Slice("foobar"), regex));
        assertTrue(likeVarchar(offsetHeapSlice("foobar"), regex));

        assertFunction("'foob' LIKE 'f%b__'", BOOLEAN, false);
        assertFunction("'foob' LIKE 'f%b'", BOOLEAN, true);
    }

    @Test
    public void testLikeChar()
    {
        JoniRegexp regex = LikeFunctions.compileLikePattern(utf8Slice("f%b__"));
        assertTrue(likeChar(6L, utf8Slice("foobar"), regex));
        assertTrue(likeChar(6L, offsetHeapSlice("foobar"), regex));
        assertTrue(likeChar(6L, utf8Slice("foob"), regex));
        assertTrue(likeChar(6L, offsetHeapSlice("foob"), regex));
        assertFalse(likeChar(7L, utf8Slice("foob"), regex));
        assertFalse(likeChar(7L, offsetHeapSlice("foob"), regex));

        assertFunction("cast('foob' as char(6)) LIKE 'f%b__'", BOOLEAN, true);
        assertFunction("cast('foob' as char(7)) LIKE 'f%b__'", BOOLEAN, false);
    }

    @Test
    public void testLikeSpacesInPattern()
    {
        JoniRegexp regex = LikeFunctions.compileLikePattern(utf8Slice("ala  "));
        assertTrue(likeVarchar(utf8Slice("ala  "), regex));
        assertFalse(likeVarchar(utf8Slice("ala"), regex));

        regex = LikeFunctions.likePattern(5L, utf8Slice("ala"));
        assertTrue(likeVarchar(utf8Slice("ala  "), regex));
        assertFalse(likeVarchar(utf8Slice("ala"), regex));
    }

    @Test
    public void testLikeNewlineInPattern()
    {
        JoniRegexp regex = LikeFunctions.compileLikePattern(utf8Slice("%o\nbar"));
        assertTrue(likeVarchar(utf8Slice("foo\nbar"), regex));
    }

    @Test
    public void testLikeNewlineBeforeMatch()
    {
        JoniRegexp regex = LikeFunctions.compileLikePattern(utf8Slice("%b%"));
        assertTrue(likeVarchar(utf8Slice("foo\nbar"), regex));
    }

    @Test
    public void testLikeNewlineInMatch()
    {
        JoniRegexp regex = LikeFunctions.compileLikePattern(utf8Slice("f%b%"));
        assertTrue(likeVarchar(utf8Slice("foo\nbar"), regex));
    }

    @Test(timeOut = 1000)
    public void testLikeUtf8Pattern()
    {
        JoniRegexp regex = likePattern(utf8Slice("%\u540d\u8a89%"), utf8Slice("\\"));
        assertFalse(likeVarchar(utf8Slice("foo"), regex));
    }

    @SuppressWarnings("NumericCastThatLosesPrecision")
    @Test(timeOut = 1000)
    public void testLikeInvalidUtf8Value()
    {
        Slice value = Slices.wrappedBuffer(new byte[] {'a', 'b', 'c', (byte) 0xFF, 'x', 'y'});
        JoniRegexp regex = likePattern(utf8Slice("%b%"), utf8Slice("\\"));
        assertTrue(likeVarchar(value, regex));
    }

    @Test
    public void testBackslashesNoSpecialTreatment()
    {
        JoniRegexp regex = LikeFunctions.compileLikePattern(utf8Slice("\\abc\\/\\\\"));
        assertTrue(likeVarchar(utf8Slice("\\abc\\/\\\\"), regex));
    }

    @Test
    public void testSelfEscaping()
    {
        JoniRegexp regex = likePattern(utf8Slice("\\\\abc\\%"), utf8Slice("\\"));
        assertTrue(likeVarchar(utf8Slice("\\abc%"), regex));
    }

    @Test
    public void testAlternateEscapedCharacters()
    {
        JoniRegexp regex = likePattern(utf8Slice("xxx%x_abcxx"), utf8Slice("x"));
        assertTrue(likeVarchar(utf8Slice("x%_abcx"), regex));
    }

    @Test
    public void testInvalidLikePattern()
    {
        assertThatThrownBy(() -> likePattern(utf8Slice("#"), utf8Slice("#")))
                .isInstanceOf(PrestoException.class)
                .hasMessage("Escape character must be followed by '%', '_' or the escape character itself");
        assertThatThrownBy(() -> likePattern(utf8Slice("abc#abc"), utf8Slice("#")))
                .isInstanceOf(PrestoException.class)
                .hasMessage("Escape character must be followed by '%', '_' or the escape character itself");
        assertThatThrownBy(() -> likePattern(utf8Slice("abc#"), utf8Slice("#")))
                .isInstanceOf(PrestoException.class)
                .hasMessage("Escape character must be followed by '%', '_' or the escape character itself");
    }

    @Test
    public void testIsLikePattern()
    {
        assertFalse(isLikePattern(utf8Slice("abc"), Optional.empty()));
        assertFalse(isLikePattern(utf8Slice("abc#_def"), Optional.of(utf8Slice("#"))));
        assertFalse(isLikePattern(utf8Slice("abc##def"), Optional.of(utf8Slice("#"))));
        assertFalse(isLikePattern(utf8Slice("abc#%def"), Optional.of(utf8Slice("#"))));
        assertTrue(isLikePattern(utf8Slice("abc%def"), Optional.empty()));
        assertTrue(isLikePattern(utf8Slice("abcdef_"), Optional.empty()));
        assertTrue(isLikePattern(utf8Slice("abcdef##_"), Optional.of(utf8Slice("#"))));
        assertTrue(isLikePattern(utf8Slice("%abcdef#_"), Optional.of(utf8Slice("#"))));
        assertThatThrownBy(() -> isLikePattern(utf8Slice("#"), Optional.of(utf8Slice("#"))))
                .isInstanceOf(PrestoException.class)
                .hasMessage("Escape character must be followed by '%', '_' or the escape character itself");
        assertThatThrownBy(() -> isLikePattern(utf8Slice("abc#abc"), Optional.of(utf8Slice("#"))))
                .isInstanceOf(PrestoException.class)
                .hasMessage("Escape character must be followed by '%', '_' or the escape character itself");
        assertThatThrownBy(() -> isLikePattern(utf8Slice("abc#"), Optional.of(utf8Slice("#"))))
                .isInstanceOf(PrestoException.class)
                .hasMessage("Escape character must be followed by '%', '_' or the escape character itself");
    }

    @Test
    public void testPatternConstantPrefixBytes()
    {
        assertEquals(patternConstantPrefixBytes(utf8Slice("abc"), Optional.empty()), 3);
        assertEquals(patternConstantPrefixBytes(utf8Slice("abc#_def"), Optional.of(utf8Slice("#"))), 8);
        assertEquals(patternConstantPrefixBytes(utf8Slice("abc##def"), Optional.of(utf8Slice("#"))), 8);
        assertEquals(patternConstantPrefixBytes(utf8Slice("abc#%def"), Optional.of(utf8Slice("#"))), 8);
        assertEquals(patternConstantPrefixBytes(utf8Slice("abc%def"), Optional.empty()), 3);
        assertEquals(patternConstantPrefixBytes(utf8Slice("abcdef_"), Optional.empty()), 6);
        assertEquals(patternConstantPrefixBytes(utf8Slice("abcdef##_"), Optional.of(utf8Slice("#"))), 8);
        assertEquals(patternConstantPrefixBytes(utf8Slice("%abcdef#_"), Optional.of(utf8Slice("#"))), 0);
        assertThatThrownBy(() -> patternConstantPrefixBytes(utf8Slice("#"), Optional.of(utf8Slice("#"))))
                .isInstanceOf(PrestoException.class)
                .hasMessage("Escape character must be followed by '%', '_' or the escape character itself");
        assertThatThrownBy(() -> patternConstantPrefixBytes(utf8Slice("abc#abc"), Optional.of(utf8Slice("#"))))
                .isInstanceOf(PrestoException.class)
                .hasMessage("Escape character must be followed by '%', '_' or the escape character itself");
        assertThatThrownBy(() -> patternConstantPrefixBytes(utf8Slice("abc#"), Optional.of(utf8Slice("#"))))
                .isInstanceOf(PrestoException.class)
                .hasMessage("Escape character must be followed by '%', '_' or the escape character itself");
    }

    @Test
    public void testUnescapeValidLikePattern()
    {
        assertEquals(unescapeLiteralLikePattern(utf8Slice("abc"), Optional.empty()), utf8Slice("abc"));
        assertEquals(unescapeLiteralLikePattern(utf8Slice("abc#_"), Optional.of(utf8Slice("#"))), utf8Slice("abc_"));
        assertEquals(unescapeLiteralLikePattern(utf8Slice("a##bc#_"), Optional.of(utf8Slice("#"))), utf8Slice("a#bc_"));
        assertEquals(unescapeLiteralLikePattern(utf8Slice("a###_bc"), Optional.of(utf8Slice("#"))), utf8Slice("a#_bc"));
    }
}
