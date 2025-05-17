package org.joymutlu.joyfulconverter;

import java.io.File;
import java.util.List;

import static java.util.Collections.emptyList;

public record WalkResult(
        List<File> filesForProcess,
        WalkResultStatus status,
        ResultInfo info
) {
    public static WalkResult ofFailure(String title, String message) {
        return new WalkResult(emptyList(), WalkResultStatus.FAILURE, new ResultInfo(title, message));
    }

    public static WalkResult ofSuccess(List<File> result) {
        return new WalkResult(result, WalkResultStatus.SUCCESS, null);
    }

    public static WalkResult ofInfo(String title, String message) {
        return new WalkResult(emptyList(), WalkResultStatus.INFO, new ResultInfo(title, message));
    }

    public boolean isFailure() {
        return status == WalkResultStatus.FAILURE;
    }
}
