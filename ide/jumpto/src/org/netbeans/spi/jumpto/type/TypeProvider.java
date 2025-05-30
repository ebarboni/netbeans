/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.netbeans.spi.jumpto.type;

import java.util.Collection;
import java.util.List;
import org.netbeans.api.annotations.common.NonNull;
import org.netbeans.api.project.Project;
import org.netbeans.modules.jumpto.type.TypeProviderAccessor;
import org.openide.util.Parameters;

/**
 * A Type Provider participates in the Goto Type dialog by providing TypeDescriptors,
 * one for each matched type, when asked to do so.
 *
 * The Type Providers are registered in Lookup.
 *
 * XXX Should we return a Collection rather than a List?
 *
 * @author Tor Norbye
 */
public interface TypeProvider {
    /**
     * Describe this provider with an internal name, in case we want to provide
     * some kind of programmatic filtering (e.g. a Java EE dialog wanting to include
     * or omit specific type providers, without relying on class names or
     * localized display names)
     *
     * @return An internal String uniquely identifying this type provider, such as
     *   "java"
     */
    String name();

    /**
     * Describe this provider for the user, in case we want to offer filtering
     * capabilities in the Go To Type dialog
     *
     * @return A display name describing the types being provided by this TypeProvider,
     *  such as "Java Types", "Ruby Types", etc.
     */
    String getDisplayName();

    /**
     * Compute a list of TypeDescriptors that match the given search text for the given
     * search type. This might be a slow operation, and the infrastructure may end
     * up calling {@link #cancel} on the same type provider during the operation, in which
     * case the method can return incomplete results. If there is a "current project",
     * the Go To Type infrastructure will perform the search in two passes; first it
     * will call {@link TypeDescriptor#getTypeName()} with the current project, which should be a reasonably
     * fast search, and display those types first. It will then call the method again
     * with a null project, which should return all types.
     * <p>
     * Note that a useful performance optimization is for the TypeProvider to cache
     * a few of its most recent search results, and if the next search (e.g. more user
     * keystrokes) is a simple narrowing of the search, just filter the previous search
     * result. There is an explicit {@link #cleanup} call that the Go To Type dialog
     * will make at the end of the dialog interaction, which can be used to clean up the cache.
     *
     * @param context search context containg search text and type, optionally project
     * @param result  filled with type descriptors and optional message
     */
    void computeTypeNames(Context context, Result result);

    /**
     * Cancel the current operation, if possible. This might be called if the user
     * has typed something (including the backspace key) which makes the current
     * search obsolete and a new one should be initiated.
     */
    void cancel();


    /**
     * The Go To Type dialog is dismissed for now - free up resources if applicable.
     * (A new "session" will be indicated by a new call to getTypeNames.)
     *
     * This allows the TypeProvider to cache its most recent search result, and if the next
     * search is simply a narrower search, it can just filter the previous result.
     */
    void cleanup();


    /**
     * Represents search context.
     * Contains search type (such as prefix, regexp), search text and
     * optionally project where to search.
     *
     * @since 1.5
     */
    public static final class Context extends Object {
        private final Project project;
        private final String text;
        private final SearchType type;

        static {
            TypeProviderAccessor.DEFAULT = new TypeProviderAccessor() {
                @Override
                public Context createContext(Project p, String text, SearchType t) {
                    return new Context(p, text, t);
                }

                @Override
                @NonNull
                public Result createResult(
                        @NonNull final Collection<? super TypeDescriptor> result,
                        @NonNull final String[] message,
                        @NonNull final Context context) {
                    return new Result(result, message, context);
                }

                @Override
                public int getRetry(Result result) {
                    return result.retry;
                }

                @NonNull
                public String getHighlightText(@NonNull final TypeDescriptor td) {
                    return td.getHighlightText();
                }

                @Override
                public void setHighlightText(TypeDescriptor td, String text) {
                    td.setHighlightText(text);
                }
            };
        }

        Context(Project project, String text, SearchType type) {
            this.project = project;
            this.text = text;
            this.type = type;
        }

        /**
         * Return project representing scope of search, if null, the search is not
         * limited.
         *
         * @return project If not null, the type search is limited to the given project.
         */
        public Project getProject() { return project; }

        /**
          * Return the text used for search.
          *
          * @return The text used for the search; e.g. when getSearchType() == SearchType.PREFIX,
          *   text is the prefix that all returned types should start with.
          */
        public String getText() { return text; }

        /**
         * Return the type of search.
         *
         * @return Type of search performed, such as prefix, regexp or camel case.
         */
        public SearchType getSearchType() { return type; }
    }

    /**
     * Represents a collection of <code>TypeDescriptor</code>s that match
     * the given search criteria. Moreover, it can contain message
     * for the user, such as an incomplete search result.
     *
     * @since 1.5
     */
    public static final class Result extends Object {

        private Collection<? super TypeDescriptor> result;
        private String[] message;
        private int retry;
        private String highlightText;
        private boolean highlightTextAlreadySet;
        private boolean modified;

        Result(
                @NonNull final Collection<? super TypeDescriptor> result,
                @NonNull final String[] message,
                @NonNull final Context context) {
            Parameters.notNull("result", result);   //NOI18N
            Parameters.notNull("message", message); //NOI18N
            Parameters.notNull("context", context); //NOI18N
            if (message.length != 1) {
                throw new IllegalArgumentException("Message.length != 1");  //NOI18N
            }
            this.result = result;
            this.message = message;
            this.highlightText = context.getText();
        }

        /**
         * Optional message. It can inform the user about result, e.g.
         * that result can be incomplete etc.
         *
         * @param  msg  message
         */
        public void setMessage(String msg) {
            message[0] = msg;
        }

        /**
          * Adds result descriptor.
          *
          * @param  typeDescriptor  type descriptor to be added to result
          */
        public void addResult(@NonNull final TypeDescriptor typeDescriptor) {
            typeDescriptor.setHighlightText(highlightText);
            result.add(typeDescriptor);
            modified = true;
        }

        /**
          * Adds list of result descriptors.
          *
          * @param  typeDescriptors  type descriptor to be added to result
          */
        public void addResult(@NonNull final List<? extends TypeDescriptor> typeDescriptors) {
            for (TypeDescriptor typeDescriptor : typeDescriptors) {
                addResult(typeDescriptor);
            }
        }

        /**
         * Notify caller that a provider should be called again because
         * of incomplete or inaccurate results.
         *
         * Method can be used when long running task blocks the provider
         * to complete the data.
         *
         * @since 1.14
         */
        public void pendingResult() {
            retry = 2000;
        }

        /**
         * Sets a text to highlight in the Go To Type panel.
         * By default the highlight text matches the text to search {@link Context#getText()}
         * and {@link TypeProvider} has no need to call this method. When the
         * {@link TypeProvider} changes the text to search and uses a part of it just as a
         * restriction it has to call the method to specify the real search text.
         * For example Java {@link TypeProvider} splits the following text to search "java.lang.Str"
         * to restriction regexp for package "java.lang" and a new search text "Str". In order to let
         * the infrastructure correctly highlight found elements the Java {@link TypeProvider}
         * needs to call {@link Result#setHighlightText(java.lang.String)}.
         * @param textToHighlight the text to highlight
         * @throws IllegalStateException when some result was already added or the highlight text
         * was already set.
         * @since 1.38
         */
        public void setHighlightText(@NonNull final String textToHighlight) {
            Parameters.notNull("textToHighlight", textToHighlight); //NOI18N
            if (modified) {
                throw new IllegalStateException("Calling setHighlightText after addResult");    //NOI18N
            }
            if (highlightTextAlreadySet) {
                throw new IllegalStateException("Highlight text already set");  //NOI18N
            }
            this.highlightText = textToHighlight;
            this.highlightTextAlreadySet = true;
        }
    }

}
