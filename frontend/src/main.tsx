import React from 'react';
import ReactDOM from 'react-dom/client';
import './styles/tokens.css';
import { AppErrorBoundary } from './components/errors/AppErrorBoundary';
import App from './App';

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <AppErrorBoundary>
      <App />
    </AppErrorBoundary>
  </React.StrictMode>
);
