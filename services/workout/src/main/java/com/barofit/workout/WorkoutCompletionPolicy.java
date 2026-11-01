package com.barofit.workout;

import com.barofit.workout.WorkoutDtos.SetResult;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import tools.jackson.databind.JsonNode;

final class WorkoutCompletionPolicy {
    private WorkoutCompletionPolicy() {}

    static boolean qualifies(JsonNode snapshot, Set<String> performedSetIds) {
        int plannedExercises = 0;
        int completedExercises = 0;
        for (JsonNode exercise : snapshot.path("exercises")) {
            JsonNode sets = exercise.path("sets");
            if (!sets.isArray() || sets.isEmpty()) continue;
            plannedExercises++;
            boolean allSetsPerformed = true;
            for (JsonNode set : sets) {
                if (!performedSetIds.contains(set.path("id").asText())) {
                    allSetsPerformed = false;
                    break;
                }
            }
            if (allSetsPerformed) completedExercises++;
        }
        return plannedExercises > 0
            && completedExercises >= Math.max(1, Math.round(plannedExercises * 0.7f));
    }

    static boolean qualifies(JsonNode snapshot, List<SetResult> sets) {
        Set<String> performed = sets.stream().filter(set ->
            (set.actualReps() != null && set.actualReps() > 0)
                || (set.actualDurationSeconds() != null && set.actualDurationSeconds() > 0))
            .map(set -> set.routineSetId().toString()).collect(Collectors.toSet());
        return qualifies(snapshot, performed);
    }
}
