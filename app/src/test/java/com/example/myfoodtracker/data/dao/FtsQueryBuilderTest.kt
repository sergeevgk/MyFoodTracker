package com.example.myfoodtracker.data.dao

import org.junit.Assert.*
import org.junit.Test

class FtsQueryBuilderTest {

    @Test
    fun build_twoTerms_returnsPerTermPrefixMatch() {
        assertEquals("\"chick\"* \"bre\"*", FtsQueryBuilder.build("chick bre"))
    }

    @Test
    fun build_outOfOrderTerms_preservesOrderIndependently() {
        assertEquals("\"breast\"* \"chicken\"*", FtsQueryBuilder.build("breast chicken"))
    }

    @Test
    fun build_singleTerm_returnsSinglePrefix() {
        assertEquals("\"apple\"*", FtsQueryBuilder.build("apple"))
    }

    @Test
    fun build_extraWhitespace_collapsesToSingleSpace() {
        assertEquals("\"chick\"* \"bre\"*", FtsQueryBuilder.build("  chick   bre  "))
    }

    @Test
    fun build_embeddedQuote_doublesQuote() {
        assertEquals("\"a\"\"b\"*", FtsQueryBuilder.build("a\"b"))
    }

    @Test
    fun build_ftsOperators_quotedAsLiterals() {
        assertEquals("\"AND\"* \"OR\"* \"NOT\"*", FtsQueryBuilder.build("AND OR NOT"))
    }

    @Test
    fun build_specialChars_keptInsideQuotedTerm() {
        assertEquals("\"C++\"*", FtsQueryBuilder.build("C++"))
    }

    @Test
    fun build_blank_returnsNull() {
        assertNull(FtsQueryBuilder.build(""))
        assertNull(FtsQueryBuilder.build("   "))
    }

    @Test
    fun build_punctuationOnly_returnsNull() {
        assertNull(FtsQueryBuilder.build("..."))
        assertNull(FtsQueryBuilder.build("***"))
        assertNull(FtsQueryBuilder.build("\"\"\""))
    }

    @Test
    fun build_mixedNoiseAndTerm_keepsOnlySearchableTerms() {
        assertEquals("\"bre\"*", FtsQueryBuilder.build("... bre ***"))
    }
}
