package tech.cryptonomic.conseil.tezos

import org.scalatest.{WordSpec, Matchers}
import com.typesafe.scalalogging.LazyLogging
import tech.cryptonomic.conseil.tezos.Api._

class ApiFilteringSpec extends WordSpec with Matchers with LazyLogging {


  "ApiFiltering" should {

    "identify a Filter that affects blocks" in {
      val nonBlocksFilter = allIncludingFilter.copy(
        blockIDs = Set.empty,
        levels = Set.empty,
        chainIDs = Set.empty,
        protocols = Set.empty
      )

      ApiFiltering.isBlockFilter(nonBlocksFilter) shouldBe false
      ApiFiltering.isBlockFilter(emptyFilter) shouldBe false
      ApiFiltering.isBlockFilter(allIncludingFilter) shouldBe true
      ApiFiltering.isBlockFilter(Filter(blockIDs = stringFilter)) shouldBe true
      ApiFiltering.isBlockFilter(Filter(levels = intFilter)) shouldBe true
      ApiFiltering.isBlockFilter(Filter(chainIDs = stringFilter)) shouldBe true
      ApiFiltering.isBlockFilter(Filter(protocols = stringFilter)) shouldBe true

    }

    "identify a Filter that affects operation groups" in {
      val nonGroupsFilter = allIncludingFilter.copy(
        operationGroupIDs = Set.empty,
        operationSources = Set.empty
      )

      ApiFiltering.isOperationGroupFilter(nonGroupsFilter) shouldBe false
      ApiFiltering.isOperationGroupFilter(emptyFilter) shouldBe false
      ApiFiltering.isOperationGroupFilter(allIncludingFilter) shouldBe true
      ApiFiltering.isOperationGroupFilter(Filter(operationGroupIDs = stringFilter)) shouldBe true
      ApiFiltering.isOperationGroupFilter(Filter(operationSources = stringFilter)) shouldBe true

    }

    "identify a Filter that affects operations" in {
      val nonOperationsFilter = allIncludingFilter.copy(
        operationSources = Set.empty,
        operationDestinations = Set.empty,
        operationKinds = Set.empty
      )

      ApiFiltering.isOperationFilter(nonOperationsFilter) shouldBe false
      ApiFiltering.isOperationFilter(emptyFilter) shouldBe false
      ApiFiltering.isOperationFilter(allIncludingFilter) shouldBe true
      ApiFiltering.isOperationFilter(Filter(operationSources = stringFilter)) shouldBe true
      ApiFiltering.isOperationFilter(Filter(operationDestinations = stringFilter)) shouldBe true
      ApiFiltering.isOperationFilter(Filter(operationKinds = stringFilter)) shouldBe true

    }

    "identify a Filter that affects accounts" in {
      val nonOperationsFilter = allIncludingFilter.copy(
        accountDelegates = Set.empty,
        accountIDs = Set.empty,
        accountManagers = Set.empty
      )

      ApiFiltering.isAccountFilter(nonOperationsFilter) shouldBe false
      ApiFiltering.isAccountFilter(emptyFilter) shouldBe false
      ApiFiltering.isAccountFilter(allIncludingFilter) shouldBe true
      ApiFiltering.isAccountFilter(Filter(accountDelegates = stringFilter)) shouldBe true
      ApiFiltering.isAccountFilter(Filter(accountIDs = stringFilter)) shouldBe true
      ApiFiltering.isAccountFilter(Filter(accountManagers = stringFilter)) shouldBe true

    }

    "extract a filter limit when specified" in {
      val testValue = 1
      ApiFiltering.getFilterLimit(Filter(limit = Some(testValue))) shouldBe testValue
    }

    "extract the default filter limit if none is specified" in {
      ApiFiltering.getFilterLimit(emptyFilter) shouldBe Filter.defaultLimit
    }

  }

  val stringFilter = Set("filter")
  val intFilter = Set(1)
  val emptyFilter = Filter()
  val allIncludingFilter = Filter(
    blockIDs = stringFilter,
    levels = intFilter,
    chainIDs = stringFilter,
    protocols = stringFilter,
    operationGroupIDs = stringFilter,
    operationSources = stringFilter,
    operationDestinations = stringFilter,
    operationParticipants = stringFilter,
    operationKinds = stringFilter,
    accountIDs = stringFilter,
    accountManagers = stringFilter,
    accountDelegates = stringFilter
  )

}