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
package io.prestosql.elasticsearch.decoders;

import io.prestosql.spi.PrestoException;
import io.prestosql.spi.block.BlockBuilder;
import org.elasticsearch.search.SearchHit;

import java.util.function.Supplier;

import static io.prestosql.spi.StandardErrorCode.TYPE_MISMATCH;
import static io.prestosql.spi.type.BooleanType.BOOLEAN;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

public class BooleanDecoder
        implements Decoder
{
    private final String path;

    public BooleanDecoder(String path)
    {
        this.path = requireNonNull(path, "path is null");
    }

    @Override
    public void decode(SearchHit hit, Supplier<Object> getter, BlockBuilder output)
    {
        Object value = getter.get();
        if (value == null) {
            output.appendNull();
        }
        else if (value instanceof Boolean) {
            BOOLEAN.writeBoolean(output, (Boolean) value);
        }
        else if (value instanceof String) {
            if (value.equals("true")) {
                BOOLEAN.writeBoolean(output, true);
            }
            else if (value.equals("false") || value.equals("")) {
                BOOLEAN.writeBoolean(output, false);
            }
            else {
                throw new PrestoException(TYPE_MISMATCH, format("Cannot parse value for field '%s' as BOOLEAN: %s", path, value));
            }
        }
        else {
            throw new PrestoException(TYPE_MISMATCH, format("Expected a boolean value for field %s of type BOOLEAN: %s [%s]", path, value, value.getClass().getSimpleName()));
        }
    }
}
