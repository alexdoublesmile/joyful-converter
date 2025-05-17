package org.joymutlu.joyfulconverter;

import java.util.UUID;

public record VideoUnit(
        UUID id,
        String name,
        String author,
        Short year,
        VideoContainerType containerType,
        Short unitNumber,
        VideoUnitGroup group
) {
}
