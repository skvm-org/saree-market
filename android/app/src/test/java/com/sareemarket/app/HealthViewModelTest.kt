package com.sareemarket.app

import com.sareemarket.app.data.HealthRepository
import com.sareemarket.app.data.HealthResponse
import com.sareemarket.app.ui.health.HealthUiState
import com.sareemarket.app.ui.health.HealthViewModel
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HealthViewModelTest {

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun showsLoadedStatusWhenApiIsUp() {
        val vm = HealthViewModel(HealthRepository(FakeHealthApi { HealthResponse("UP") }))
        assertEquals(HealthUiState.Loaded("UP"), vm.state.value)
    }

    @Test
    fun showsFailureThenRecoversOnRefresh() {
        var up = false
        val api = FakeHealthApi { if (up) HealthResponse("UP") else throw IOException("refused") }
        val vm = HealthViewModel(HealthRepository(api))
        assertEquals(HealthUiState.Failed("refused"), vm.state.value)

        up = true
        vm.refresh()
        assertEquals(HealthUiState.Loaded("UP"), vm.state.value)
        assertEquals(2, api.calls)
    }
}
