import { LoadingButton } from '@mui/lab';
import {
  Alert,
  AlertTitle,
  Box,
  Chip,
  Collapse,
  Divider,
  IconButton,
  InputAdornment,
  LinearProgress,
  Stack,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material';
import { useSnackbar } from 'notistack';
import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import Iconify from '../../components/iconify';
import AuthService from '../../services/AuthService';

// ─── backend URL helper ───────────────────────────────────────────────────────
const BACKEND_HOST = process.env.REACT_APP_API_URL
  ? `https://${process.env.REACT_APP_API_URL}`
  : 'http://localhost:8080';
const SWAGGER_URL = `${BACKEND_HOST}/swagger-ui/index.html`;

// How long (ms) before we show the "server waking up" banner
const WAKEUP_THRESHOLD_MS = 4000;

export default function LoginForm() {
  const defaultValues = { username: '', password: '' };

  const navigate = useNavigate();
  const [showPassword, setShowPassword] = useState(false);
  const [loading, setLoading] = useState(false);
  const [showWakeUp, setShowWakeUp] = useState(false);
  const [progress, setProgress] = useState(0);
  const [formValues, setFormValues] = useState(defaultValues);
  const { enqueueSnackbar } = useSnackbar();

  const wakeUpTimerRef = useRef(null);
  const progressIntervalRef = useRef(null);

  // ── cleanup on unmount ──────────────────────────────────────────────────────
  useEffect(() => () => {
    clearTimeout(wakeUpTimerRef.current);
    clearInterval(progressIntervalRef.current);
  }, []);

  // ── start / stop wake-up UX ─────────────────────────────────────────────────
  const startWakeUpTimer = () => {
    setProgress(0);
    wakeUpTimerRef.current = setTimeout(() => {
      setShowWakeUp(true);
      // animate progress bar from 0 → 95 over ~25 s
      progressIntervalRef.current = setInterval(() => {
        setProgress((prev) => (prev < 95 ? prev + 1 : 95));
      }, 280);
    }, WAKEUP_THRESHOLD_MS);
  };

  const stopWakeUpTimer = () => {
    clearTimeout(wakeUpTimerRef.current);
    clearInterval(progressIntervalRef.current);
    setShowWakeUp(false);
    setProgress(0);
  };

  // ── core login logic ────────────────────────────────────────────────────────
  const performLogin = (payload) => {
    setLoading(true);
    startWakeUpTimer();

    AuthService.login(payload)
      .then(() => {
        stopWakeUpTimer();
        navigate('/');
      })
      .catch((error) => {
        stopWakeUpTimer();
        if (error.response?.data?.errors) {
          error.response.data.errors.forEach((e) =>
            enqueueSnackbar(e.message, { variant: 'error' })
          );
        } else if (error.response?.data?.message) {
          enqueueSnackbar(error.response.data.message, { variant: 'error' });
        } else if (
          error.code === 'ECONNABORTED' ||
          error.code === 'ERR_NETWORK' ||
          !error.response
        ) {
          enqueueSnackbar(
            'Server is still waking up – please try again in a moment.',
            { variant: 'warning', autoHideDuration: 6000 }
          );
        } else {
          enqueueSnackbar(error.message || 'Login failed. Please try again.', {
            variant: 'error',
          });
        }
      })
      .finally(() => setLoading(false));
  };

  const handleInputChange = (e) => {
    const { name, value } = e.target;
    setFormValues({ ...formValues, [name]: value });
  };

  const handleSubmit = (event) => {
    event.preventDefault();
    performLogin({
      username: formValues.username.trim().toLowerCase(),
      password: formValues.password,
    });
  };

  const handleDemoLogin = (event) => {
    event.preventDefault();
    performLogin({ username: 'recruiter', password: 'demo123' });
  };

  // ── render ──────────────────────────────────────────────────────────────────
  return (
    <>
      {/* ── Server wake-up banner ─────────────────────────────────────── */}
      <Collapse in={showWakeUp} sx={{ mb: showWakeUp ? 2 : 0 }}>
        <Alert
          severity="info"
          icon={<Iconify icon="line-md:loading-loop" width={22} />}
          sx={{
            borderRadius: 2,
            background: 'linear-gradient(135deg, #e3f2fd 0%, #fce4ec 100%)',
            border: '1px solid #90caf9',
            mb: 1,
          }}
        >
          <AlertTitle sx={{ fontWeight: 700 }}>
            🚀 Server is waking up…
          </AlertTitle>
          <Typography variant="body2" sx={{ mb: 1 }}>
            The free-tier backend spins down after inactivity. This typically
            takes&nbsp;<strong>15–30 seconds</strong>.&nbsp;Please hang tight!
          </Typography>
          <LinearProgress
            variant="determinate"
            value={progress}
            sx={{ borderRadius: 1, height: 6 }}
          />
        </Alert>
      </Collapse>

      {/* ── Demo credentials hint ─────────────────────────────────────── */}
      <Box
        sx={{
          p: 1.5,
          mb: 2,
          borderRadius: 2,
          background: 'linear-gradient(135deg, #f3e5f5 0%, #e8f5e9 100%)',
          border: '1px dashed #ab47bc',
          display: 'flex',
          alignItems: 'center',
          gap: 1,
          flexWrap: 'wrap',
        }}
      >
        <Iconify icon="mdi:badge-account-outline" width={20} sx={{ color: '#7b1fa2' }} />
        <Typography variant="caption" sx={{ fontWeight: 600, color: '#4a148c' }}>
          Demo credentials:
        </Typography>
        <Chip label="recruiter" size="small" color="secondary" variant="outlined" />
        <Chip label="demo123" size="small" color="secondary" variant="outlined" />
      </Box>

      {/* ── Form fields ───────────────────────────────────────────────── */}
      <Stack spacing={3}>
        <TextField
          id="username"
          name="username"
          label="Username"
          autoComplete="username"
          required
          autoFocus
          value={formValues.username}
          onChange={handleInputChange}
        />
        <TextField
          id="password"
          name="password"
          label="Password"
          autoComplete="current-password"
          type={showPassword ? 'text' : 'password'}
          required
          value={formValues.password}
          onChange={handleInputChange}
          InputProps={{
            endAdornment: (
              <InputAdornment position="end">
                <IconButton
                  onClick={() => setShowPassword(!showPassword)}
                  edge="end"
                >
                  <Iconify
                    icon={showPassword ? 'eva:eye-fill' : 'eva:eye-off-fill'}
                  />
                </IconButton>
              </InputAdornment>
            ),
          }}
        />
      </Stack>

      <Stack direction="row" alignItems="center" justifyContent="space-between" sx={{ my: 2 }} />

      {/* ── Login buttons ─────────────────────────────────────────────── */}
      <LoadingButton
        fullWidth
        size="large"
        type="submit"
        variant="contained"
        loading={loading}
        onClick={handleSubmit}
      >
        Log in
      </LoadingButton>

      <LoadingButton
        id="demo-recruiter-login-btn"
        fullWidth
        size="large"
        variant="outlined"
        color="secondary"
        loading={loading}
        onClick={handleDemoLogin}
        sx={{ mt: 2 }}
        startIcon={
          !loading && (
            <Iconify icon="mdi:account-tie-outline" width={20} />
          )
        }
      >
        Demo Recruiter Login
      </LoadingButton>

      {/* ── Divider ───────────────────────────────────────────────────── */}
      <Divider sx={{ my: 3 }}>
        <Typography variant="caption" sx={{ color: 'text.secondary' }}>
          Explore the API
        </Typography>
      </Divider>

      {/* ── Swagger link ─────────────────────────────────────────────── */}
      <Tooltip title="Opens the interactive REST API documentation in a new tab">
        <LoadingButton
          id="swagger-api-btn"
          fullWidth
          size="large"
          variant="outlined"
          color="info"
          href={SWAGGER_URL}
          target="_blank"
          rel="noopener noreferrer"
          startIcon={<Iconify icon="simple-icons:swagger" width={20} />}
          sx={{
            borderStyle: 'dashed',
            '&:hover': { borderStyle: 'solid' },
          }}
        >
          View Swagger API Docs
        </LoadingButton>
      </Tooltip>
    </>
  );
}
