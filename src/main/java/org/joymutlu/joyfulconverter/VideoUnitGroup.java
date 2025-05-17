package org.joymutlu.joyfulconverter;

import java.time.Year;
import java.util.List;
import java.util.UUID;

public record VideoUnitGroup(
        UUID id,
        String name,
        Year startYear,
        Year endYear,
        Short groupNumber,
        List<VideoUnit> videoUnitList
) {
//    getFullName()
}
