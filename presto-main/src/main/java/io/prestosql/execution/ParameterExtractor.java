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
package io.prestosql.execution;

import io.prestosql.sql.tree.DefaultTraversalVisitor;
import io.prestosql.sql.tree.Parameter;
import io.prestosql.sql.tree.Statement;

import java.util.ArrayList;
import java.util.List;

public final class ParameterExtractor
{
    private ParameterExtractor() {}

    public static int getParameterCount(Statement statement)
    {
        return getParameters(statement).size();
    }

    public static List<Parameter> getParameters(Statement statement)
    {
        ParameterExtractingVisitor parameterExtractingVisitor = new ParameterExtractingVisitor();
        parameterExtractingVisitor.process(statement, null);
        return parameterExtractingVisitor.getParameters();
    }

    private static class ParameterExtractingVisitor
            extends DefaultTraversalVisitor<Void>
    {
        private final List<Parameter> parameters = new ArrayList<>();

        public List<Parameter> getParameters()
        {
            return parameters;
        }

        @Override
        public Void visitParameter(Parameter node, Void context)
        {
            parameters.add(node);
            return null;
        }
    }
}
