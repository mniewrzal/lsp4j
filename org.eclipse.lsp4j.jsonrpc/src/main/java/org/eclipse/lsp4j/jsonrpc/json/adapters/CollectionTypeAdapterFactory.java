/*******************************************************************************
 * Copyright (c) 2016 TypeFox GmbH (http://www.typefox.io) and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.lsp4j.jsonrpc.json.adapters;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Supplier;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

/**
 * A specialized type adapter factory for collections that can handle single values.
 */
public class CollectionTypeAdapterFactory implements TypeAdapterFactory {

	@Override
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> typeToken) {
		if (!Collection.class.isAssignableFrom(typeToken.getRawType()))
			return null;

		Type[] elementTypes = TypeUtils.getElementTypes(typeToken, Collection.class);
		if (elementTypes.length != 1)
			return null;
		TypeAdapter<?> elementTypeAdapter = gson.getAdapter(TypeToken.get(elementTypes[0]));
		Supplier<Collection<Object>> constructor = getConstructor((Class<Collection<Object>>) typeToken.getRawType());
		return new Adapter(gson, elementTypes[0], elementTypeAdapter, constructor);
	}

	protected static class Adapter<E> extends TypeAdapter<Collection<E>> {

		private final Gson gson;
		private final Type elementType;
		private final TypeAdapter<E> elementTypeAdapter;
		private final Supplier<Collection<E>> constructor;

		public Adapter(Gson gson, Type elementType, TypeAdapter<E> elementTypeAdapter, Supplier<Collection<E>> constructor) {
			this.gson = gson;
			this.elementType = elementType;
			this.elementTypeAdapter = elementTypeAdapter;
			this.constructor = constructor;
		}

		@Override
		public Collection<E> read(JsonReader in) throws IOException {
			JsonToken peek = in.peek();
			if (peek == JsonToken.NULL) {
				in.nextNull();
				return null;
			} else if (peek == JsonToken.BEGIN_ARRAY) {
				Collection<E> collection = constructor.get();
				in.beginArray();
				while (in.hasNext()) {
					E instance = elementTypeAdapter.read(in);
					collection.add(instance);
				}
				in.endArray();
				return collection;
			} else {
				Collection<E> collection = constructor.get();
				E instance = elementTypeAdapter.read(in);
				collection.add(instance);
				return collection;
			}
		}

		@Override
		public void write(JsonWriter out, Collection<E> collection) throws IOException {
			if (collection == null) {
				out.nullValue();
				return;
			}
			out.beginArray();
			for (E element : collection) {
				if (element != null && elementType != element.getClass()
						&& (elementType instanceof TypeVariable<?> || elementType instanceof Class<?>)) {
					@SuppressWarnings("unchecked")
					TypeAdapter<E> runtimeTypeAdapter = (TypeAdapter<E>) gson.getAdapter(TypeToken.get(element.getClass()));
					runtimeTypeAdapter.write(out, element);
				} else {
					elementTypeAdapter.write(out, element);
				}
			}
			out.endArray();
		}
	}

	private <E> Supplier<Collection<E>> getConstructor(Class<Collection<E>> rawType) {
		try {
			Constructor<Collection<E>> constructor = rawType.getDeclaredConstructor();
			if (!constructor.isAccessible())
				constructor.setAccessible(true);
			return () -> {
				try {
					return constructor.newInstance();
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			};
		} catch (NoSuchMethodException e) {
			if (SortedSet.class.isAssignableFrom(rawType)) {
				return () -> {
					return new TreeSet<E>();
				};
			} else if (Set.class.isAssignableFrom(rawType)) {
				return () -> {
					return new LinkedHashSet<E>();
				};
			} else if (Queue.class.isAssignableFrom(rawType)) {
				return () -> {
					return new LinkedList<E>();
				};
			} else {
				return () -> {
					return new ArrayList<E>();
				};
			}
		}
	}

}
