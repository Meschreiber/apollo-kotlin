@file:JvmName("JavaOptionalAdapters")

package com.apollographql.apollo3.api.java.adapter

import com.apollographql.apollo3.api.AnyApolloAdapter
import com.apollographql.apollo3.api.ApolloAdapter
import com.apollographql.apollo3.api.ApolloAdapter.DataDeserializeContext
import com.apollographql.apollo3.api.BooleanApolloAdapter
import com.apollographql.apollo3.api.DoubleApolloAdapter
import com.apollographql.apollo3.api.IntApolloAdapter
import com.apollographql.apollo3.api.StringApolloAdapter
import com.apollographql.apollo3.api.json.JsonReader
import com.apollographql.apollo3.api.json.JsonWriter
import java.util.Optional

/**
 * An adapter for Java's [Optional]. `null` is deserialized as [Optional.empty].
 */
class JavaOptionalAdapter<T : Any>(private val wrappedAdapter: ApolloAdapter<T>) : ApolloAdapter<Optional<T>> {
  override fun fromJson(reader: JsonReader, context: DataDeserializeContext): Optional<T> {
    return if (reader.peek() == JsonReader.Token.NULL) {
      reader.skipValue()
      Optional.empty()
    } else {
      Optional.of(wrappedAdapter.fromJson(reader, context))
    }
  }

  override fun toJson(writer: JsonWriter, value: Optional<T>, context: ApolloAdapter.DataSerializeContext) {
    if (!value.isPresent) {
      writer.nullValue()
    } else {
      wrappedAdapter.toJson(writer, value.get(), context)
    }
  }
}

/*
 * Global instances of optional adapters for built-in scalar types
 */
@JvmField
val JavaOptionalStringApolloAdapter = JavaOptionalAdapter(StringApolloAdapter)

@JvmField
val JavaOptionalDoubleApolloAdapter = JavaOptionalAdapter(DoubleApolloAdapter)

@JvmField
val JavaOptionalIntApolloAdapter = JavaOptionalAdapter(IntApolloAdapter)

@JvmField
val JavaOptionalBooleanApolloAdapter = JavaOptionalAdapter(BooleanApolloAdapter)

@JvmField
val JavaOptionalAnyApolloAdapter = JavaOptionalAdapter(AnyApolloAdapter)
