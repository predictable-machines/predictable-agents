package predictable

import kotlinx.coroutines.test.runTest

object TestUtils {
    fun workflowWithEmptyState(block: suspend () -> Unit) {
        runTest {
          block()
        }
    }
}
