import { Container, Grid, Typography } from '@mui/material';
import { useState, useEffect } from 'react';
import { Helmet } from 'react-helmet-async';
import { AppWidgetSummary } from '../../sections/@dashboard/app';
import HttpService from '../../services/HttpService';

export default function Dashboard() {
  const [stats, setStats] = useState({
    totalWallets: 0,
    totalTransactions: 0,
    totalUsers: 0
  });

  useEffect(() => {
    HttpService.getWithAuth('/wallets/stats')
      .then(res => setStats(res.data || { totalWallets: 0, totalTransactions: 0, totalUsers: 0 }))
      .catch(err => console.error('Stats fetch failed', err));
  }, []);

  return (
    <>
      <Helmet>
        <title> Dashboard | e-Wallet </title>
      </Helmet>
      <Container maxWidth="xl">
        <Typography variant="h4" sx={{ mb: 5 }}>
          Dashboard
        </Typography>
        <Grid container spacing={3}>
          <Grid item xs={12} sm={6} md={3}>
            <AppWidgetSummary title="Wallets" total={stats.totalWallets} icon={'ant-design:wallet-outlined'} />
          </Grid>
          <Grid item xs={12} sm={6} md={3}>
            <AppWidgetSummary title="Users" total={stats.totalUsers} color="warning" icon={'ant-design:user-outlined'} />
          </Grid>
          <Grid item xs={12} sm={6} md={3}>
            <AppWidgetSummary
              title="Transactions (monthly)"
              total={stats.totalTransactions}
              color="info"
              icon={'ant-design:transaction-outlined'}
            />
          </Grid>
        </Grid>
      </Container>
    </>
  );
}
