package com.netflix.spinnaker.keel.services

import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.plugins.ArtifactSupplier
import com.netflix.spinnaker.keel.core.api.ManualJudgementConstraint
import com.netflix.spinnaker.keel.core.api.PipelineConstraint
import com.netflix.spinnaker.keel.core.api.TimeWindow
import com.netflix.spinnaker.keel.core.api.TimeWindowConstraint
import com.netflix.spinnaker.keel.pause.ActuationPauser
import com.netflix.spinnaker.keel.persistence.DiffFingerprintRepository
import com.netflix.spinnaker.keel.persistence.KeelRepository
import com.netflix.spinnaker.keel.test.DummyArtifact
import com.netflix.spinnaker.keel.test.DummyVersioningStrategy
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.context.ApplicationEventPublisher

class AdminServiceTests : JUnit5Minutests {
  class Fixture {
    val repository: KeelRepository = mockk(relaxed = true)
    val diffFingerprintRepository: DiffFingerprintRepository = mockk()
    val actuationPauser: ActuationPauser = mockk()
    val publisher: ApplicationEventPublisher = mockk(relaxUnitFun = true)
    private val artifactSupplier = mockk<ArtifactSupplier<DummyArtifact, DummyVersioningStrategy>>(relaxUnitFun = true)

    val application = "leapp"

    val environment = Environment(
      name = "test",
      constraints = setOf(
        ManualJudgementConstraint(),
        PipelineConstraint(pipelineId = "wowapipeline"),
        TimeWindowConstraint(windows = listOf(TimeWindow(days = "Monday")))
      )
    )

    val deliveryConfig = DeliveryConfig(
      name = "manifest",
      application = application,
      serviceAccount = "keel@spinnaker",
      artifacts = setOf(),
      environments = setOf(environment)
    )

    val subject = AdminService(
      repository,
      actuationPauser,
      diffFingerprintRepository,
      listOf(artifactSupplier),
      publisher
    )
  }

  fun adminServiceTests() = rootContext<Fixture> {
    fixture {
      Fixture()
    }

    before {
      every { repository.getDeliveryConfigForApplication(application) } returns deliveryConfig
    }

    context("forcing environment constraint reevaluation") {
      test("clears state only for stateful constraints") {
        subject.forceConstraintReevaluation(application, environment.name)

        verify(exactly = 1) { repository.deleteConstraintState(deliveryConfig.name, environment.name, "manual-judgement") }
        verify(exactly = 1) { repository.deleteConstraintState(deliveryConfig.name, environment.name, "pipeline") }
        verify(exactly = 0) { repository.deleteConstraintState(deliveryConfig.name, environment.name, "allowed-times") }
      }

      test("clears a specific constraint type when asked to") {
        subject.forceConstraintReevaluation(application, environment.name, "pipeline")

        verify(exactly = 0) { repository.deleteConstraintState(deliveryConfig.name, environment.name, "manual-judgement") }
        verify(exactly = 1) { repository.deleteConstraintState(deliveryConfig.name, environment.name, "pipeline") }
        verify(exactly = 0) { repository.deleteConstraintState(deliveryConfig.name, environment.name, "allowed-times") }
      }
    }
  }
}
