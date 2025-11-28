import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { AppLayout } from './components/AppLayout'
import { WorkflowsPage } from './pages/WorkflowsPage'
import { ExecutionsPage } from './pages/ExecutionsPage'
import { ExecutionDetailPage } from './pages/ExecutionDetailPage'
import { LaunchPage } from './pages/LaunchPage'

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<AppLayout />}>
          <Route index element={<Navigate to="/workflows" replace />} />
          <Route path="workflows" element={<WorkflowsPage />} />
          <Route path="executions" element={<ExecutionsPage />} />
          <Route path="executions/:executionId" element={<ExecutionDetailPage />} />
          <Route path="launch" element={<LaunchPage />} />
        </Route>
      </Routes>
    </BrowserRouter>
  )
}

export default App
