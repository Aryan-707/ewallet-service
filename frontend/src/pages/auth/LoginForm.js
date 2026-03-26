import { LoadingButton } from '@mui/lab';
import { IconButton, InputAdornment, Stack, TextField } from '@mui/material';
import { useSnackbar } from 'notistack';
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import Iconify from '../../components/iconify';
import AuthService from '../../services/AuthService';

export default function LoginForm() {
  const defaultValues = {
    username: '',
    password: '',
  };

  const navigate = useNavigate();
  const [showPassword, setShowPassword] = useState(false);
  const [loading, setLoading] = useState(false);
  const { enqueueSnackbar } = useSnackbar();
  const [formValues, setFormValues] = useState(defaultValues);

  const handleInputChange = (e) => {
    const { name, value } = e.target;
    setFormValues({
      ...formValues,
      [name]: value,
    });
  };

  const performLogin = (payload) => {
    setLoading(true);
    AuthService.login(payload)
      .then((response) => {
        navigate('/');
      })
      .catch((error) => {
        if (error.response?.data?.errors) {
          error.response?.data?.errors.map((e) => enqueueSnackbar(e.message, { variant: 'error' }));
        } else if (error.response?.data?.message) {
          enqueueSnackbar(error.response?.data?.message, { variant: 'error' });
        } else if (error.code === 'ECONNABORTED') {
          enqueueSnackbar('Server is waking up, please try again in a few seconds.', { variant: 'warning' });
        } else {
          enqueueSnackbar(error.message || 'Login failed. Please try again.', { variant: 'error' });
        }
      })
      .finally(() => setLoading(false));
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
    performLogin({
      username: 'recruiter',
      password: 'demo123',
    });
  };

  return (
    <>
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
                <IconButton onClick={() => setShowPassword(!showPassword)} edge="end">
                  <Iconify icon={showPassword ? 'eva:eye-fill' : 'eva:eye-off-fill'} />
                </IconButton>
              </InputAdornment>
            ),
          }}
        />
      </Stack>

      <Stack direction="row" alignItems="center" justifyContent="space-between" sx={{ my: 2 }} />

      <LoadingButton fullWidth size="large" type="submit" variant="contained" loading={loading} onClick={handleSubmit}>
        Log in
      </LoadingButton>

      <LoadingButton
        fullWidth
        size="large"
        variant="outlined"
        color="secondary"
        loading={loading}
        onClick={handleDemoLogin}
        sx={{ mt: 2 }}
      >
        Demo Recruiter Login
      </LoadingButton>
    </>
  );
}

