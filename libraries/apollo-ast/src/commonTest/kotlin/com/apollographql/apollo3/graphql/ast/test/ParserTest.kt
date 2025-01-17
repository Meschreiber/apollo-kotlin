package com.apollographql.apollo3.graphql.ast.test

import com.apollographql.apollo3.ast.GQLField
import com.apollographql.apollo3.ast.GQLFieldDefinition
import com.apollographql.apollo3.ast.GQLObjectTypeDefinition
import com.apollographql.apollo3.ast.GQLOperationDefinition
import com.apollographql.apollo3.ast.GQLStringValue
import com.apollographql.apollo3.ast.parseAsGQLDocument
import com.apollographql.apollo3.ast.parseAsGQLValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class ParserTest {
  @Test
  fun extraTokensAtEndOfFileAreDetected() {
    try {
      """
      # comment before
      query Test { field }
      ab bc
      """.trimIndent()
          .parseAsGQLDocument()
          .getOrThrow()
      fail("An exception was expected")
    } catch (e: Exception) {
      check(e.message?.contains("Unexpected token: 'name: ab'") == true)
    }
  }

  @Test
  fun extraCommentsAtEndOfFileAreOk() {
    """
      # comment before
      query Test { field }
      # comment after
      """.trimIndent()
        .parseAsGQLDocument()
        .getOrThrow()
  }

  @Test
  fun blockString() {
    val value = "\"\"\" \\\"\"\" \"\"\"".parseAsGQLValue().getOrThrow()

    assertEquals(" \"\"\" ", (value as GQLStringValue).value)
  }

  @Test
  fun blockStringIndentationIsRemoved() {
    val value = ("\"\"\"\n" +
        "  first line\n" +
        "   \n" +
        "  second line\n" +
        "   \n" +
        "\"\"\"").parseAsGQLValue().getOrThrow()

    assertEquals("first line\n \nsecond line", (value as GQLStringValue).value)
  }

  private inline fun <reified T> Any.cast() = this as T

  @Test
  fun endLineAndColumn() {
    """
      # comment before
query Test {
  # comment here
  field(
    first: 100
    )
}
      """.trimIndent()
        .parseAsGQLDocument()
        .getOrThrow()
        .definitions
        .first()
        .cast<GQLOperationDefinition>()
        .selections
        .first()
        .cast<GQLField>()
        .sourceLocation!!
        .apply {
          assertEquals(55, start)
          assertEquals(82, end)
          assertEquals(4, line)
          assertEquals(3, column)
        }
  }

  @Test
  fun endLineAndColumn2() {
    """
      type Something { 
        fieldA: String
      }
    """.trimIndent()
        .parseAsGQLDocument()
        .getOrThrow()
        .definitions
        .first()
        .cast<GQLObjectTypeDefinition>()
        .fields
        .first()
        .cast<GQLFieldDefinition>()
        .sourceLocation!!
        .apply {
          assertEquals(20, start)
          assertEquals(34, end)
          assertEquals(2, line)
          assertEquals(3, column)
        }
  }
}
