interface LoadingSpinnerProps {
  message?: string
  size?: 'small' | 'medium' | 'large'
}

/**
 * Loading spinner component with optional message
 */
export function LoadingSpinner({ message, size = 'medium' }: LoadingSpinnerProps) {
  const getSizeValue = () => {
    switch (size) {
      case 'small':
        return '20px'
      case 'large':
        return '60px'
      default:
        return '40px'
    }
  }

  return (
    <div className="loading-spinner-container">
      <div className="spinner" style={{ width: getSizeValue(), height: getSizeValue() }} />
      {message && <p className="loading-message">{message}</p>}
      <style>{`
        .loading-spinner-container {
          display: flex;
          flex-direction: column;
          align-items: center;
          justify-content: center;
          gap: 1rem;
          padding: 2rem;
        }

        .spinner {
          border: 3px solid rgba(100, 108, 255, 0.2);
          border-top-color: #646cff;
          border-radius: 50%;
          animation: spin 0.8s linear infinite;
        }

        .loading-message {
          margin: 0;
          color: #888;
          font-size: 0.9rem;
        }

        @keyframes spin {
          to {
            transform: rotate(360deg);
          }
        }
      `}</style>
    </div>
  )
}
