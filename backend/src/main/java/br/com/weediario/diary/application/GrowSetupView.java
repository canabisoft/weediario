package br.com.weediario.diary.application;

import br.com.weediario.diary.domain.model.GrowEnvironment;
import br.com.weediario.diary.domain.model.GrowSetup;
import br.com.weediario.diary.domain.model.SetupSpec;

import java.util.UUID;

public record GrowSetupView(UUID id, String name, GrowEnvironment environment, SetupSpec spec) {

    public static GrowSetupView of(GrowSetup setup) {
        return new GrowSetupView(setup.id().value(), setup.name(), setup.environment(), setup.spec());
    }
}
