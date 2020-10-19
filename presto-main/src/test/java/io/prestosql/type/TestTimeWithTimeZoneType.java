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
package io.prestosql.type;

import io.prestosql.spi.block.Block;
import io.prestosql.spi.block.BlockBuilder;
import io.prestosql.spi.type.SqlTimeWithTimeZone;

import static io.prestosql.spi.type.DateTimeEncoding.packTimeWithTimeZone;
import static io.prestosql.spi.type.DateTimeEncoding.unpackOffsetMinutes;
import static io.prestosql.spi.type.DateTimeEncoding.unpackTimeNanos;
import static io.prestosql.spi.type.TimeWithTimeZoneType.TIME_WITH_TIME_ZONE;

public class TestTimeWithTimeZoneType
        extends AbstractTestType
{
    public TestTimeWithTimeZoneType()
    {
        super(TIME_WITH_TIME_ZONE, SqlTimeWithTimeZone.class, createTestBlock());
    }

    public static Block createTestBlock()
    {
        BlockBuilder blockBuilder = TIME_WITH_TIME_ZONE.createBlockBuilder(null, 15);
        TIME_WITH_TIME_ZONE.writeLong(blockBuilder, packTimeWithTimeZone(1111, 0));
        TIME_WITH_TIME_ZONE.writeLong(blockBuilder, packTimeWithTimeZone(1111, 1));
        TIME_WITH_TIME_ZONE.writeLong(blockBuilder, packTimeWithTimeZone(1111, 2));
        TIME_WITH_TIME_ZONE.writeLong(blockBuilder, packTimeWithTimeZone(2222, 3));
        TIME_WITH_TIME_ZONE.writeLong(blockBuilder, packTimeWithTimeZone(2222, 4));
        TIME_WITH_TIME_ZONE.writeLong(blockBuilder, packTimeWithTimeZone(2222, 5));
        TIME_WITH_TIME_ZONE.writeLong(blockBuilder, packTimeWithTimeZone(2222, 6));
        TIME_WITH_TIME_ZONE.writeLong(blockBuilder, packTimeWithTimeZone(2222, 7));
        TIME_WITH_TIME_ZONE.writeLong(blockBuilder, packTimeWithTimeZone(3333, 8));
        TIME_WITH_TIME_ZONE.writeLong(blockBuilder, packTimeWithTimeZone(3333, 9));
        TIME_WITH_TIME_ZONE.writeLong(blockBuilder, packTimeWithTimeZone(4444, 10));
        return blockBuilder.build();
    }

    @Override
    protected Object getGreaterValue(Object value)
    {
        return packTimeWithTimeZone(unpackTimeNanos((Long) value) + 10, unpackOffsetMinutes((Long) value));
    }
}
