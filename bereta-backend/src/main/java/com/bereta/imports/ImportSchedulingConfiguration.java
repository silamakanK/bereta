package com.bereta.imports;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Active les tâches planifiées (purge nocturne des brouillons d'import). */
@Configuration
@EnableScheduling
public class ImportSchedulingConfiguration {
}
