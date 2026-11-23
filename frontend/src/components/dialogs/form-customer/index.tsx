'use client'
import React, { useEffect, useState } from 'react'

import {
  Alert,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogContentText,
  DialogActions,
  Box,
  TextField,
  Button,
  MenuItem,
  Grid,
  InputLabel,
  Select,
  FormControl,
  FormHelperText,
  Snackbar,
  Typography,
  Divider
} from '@mui/material'
import { useForm, Controller } from 'react-hook-form'
import { yupResolver } from '@hookform/resolvers/yup'
import * as yup from 'yup'
import axios from 'axios'

import type { CustomersType } from '@/types/apps/customerType'

const schema = yup.object().shape({
  name: yup.string().required('El nombre es obligatorio'),
  nit: yup.string().required('El NIT es obligatorio'),
  phone: yup.string().required('El teléfono es obligatorio'),
  email: yup.string().email('Email inválido').required('El email es obligatorio'),
  address: yup.string().required('La dirección es obligatoria'),
  contact: yup.string().required('El contacto es obligatorio'),
  position: yup.string().required('El cargo es obligatorio'),
  type: yup.string().required('El tipo es obligatorio'),
  fechaInicio: yup.string().required('La fecha inicial es obligatoria'),
  fechaFinal: yup.string().required('La fecha final es obligatoria'),
  descripcionContrato: yup.string().optional(),
  status: yup.string().required('El estado es obligatorio')
})

const normalizeStatus = (status: any): string => {
  if (status === true || status === 1 || status === '1' || status === 'true') return '1'
  return '0'
}

const ClienteForm = ({
  open,
  onClose,
  setOpen,
  rowSelect
}: {
  open: boolean
  onClose: () => void
  setOpen: () => void
  rowSelect: CustomersType
}) => {
  const [id, setId] = useState<any>(null)
  const [confirmOpen, setConfirmOpen] = useState(false)
  const [pendingData, setPendingData] = useState<any>(null)
  const [saving, setSaving] = useState(false)
  const [feedback, setFeedback] = useState<{
    open: boolean
    message: string
    severity: 'success' | 'error' | 'warning' | 'info'
  }>({
    open: false,
    message: '',
    severity: 'info'
  })

  const {
    control,
    handleSubmit,
    formState: { errors },
    setValue,
    reset
  } = useForm({
    resolver: yupResolver(schema),
    defaultValues: {
      name: '',
      nit: '',
      phone: '',
      email: '',
      address: '',
      contact: '',
      position: '',
      type: '',
      fechaInicio: '',
      fechaFinal: '',
      descripcionContrato: '',
      status: '1'
    }
  })

  const executeSave = async (data: any) => {
    setSaving(true)
    try {
      const token = localStorage.getItem('AuthToken')

      if (!token) {
        throw new Error('Token no disponible. Por favor, inicia sesión nuevamente.')
      }

      const method = id ? 'put' : 'post'
      const apiUrl = id
        ? `${process.env.NEXT_PUBLIC_API_URL}/customers/${id}`
        : `${process.env.NEXT_PUBLIC_API_URL}/customers`

      const payload = {
        ...data,
        status: id ? data.status : Number(data.status)
      }

      const response = await axios({
        method,
        url: apiUrl,
        data: payload,
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${token}`
        }
      })

      setFeedback({
        open: true,
        message:
          response.data?.message ||
          (id ? 'Cliente actualizado correctamente.' : 'Cliente registrado correctamente.'),
        severity: 'success'
      })

      reset()
      setId(null)
      setConfirmOpen(false)
      setPendingData(null)
      onClose()
    } catch (error: any) {
      console.error('Error al enviar los datos:', error)
      const backendMsg =
        error?.response?.data?.message || error?.message || 'Error al guardar la información del cliente.'
      setFeedback({
        open: true,
        message: backendMsg,
        severity: 'error'
      })
      setConfirmOpen(false)
    } finally {
      setSaving(false)
    }
  }

  const onSubmit = async (data: any) => {
    if (id) {
      setPendingData(data)
      setConfirmOpen(true)
    } else {
      await executeSave(data)
    }
  }

  useEffect(() => {
    const loadCustomerData = async () => {
      if (rowSelect && rowSelect.id) {
        setId(rowSelect.id)
        setValue('name', rowSelect.name || '')
        setValue('nit', rowSelect.nit || '')
        setValue('phone', rowSelect.phone || '')
        setValue('email', rowSelect.email || '')
        setValue('address', rowSelect.address || '')
        setValue('contact', rowSelect.contact || '')
        setValue('position', rowSelect.position || '')
        setValue('type', rowSelect.type !== undefined && rowSelect.type !== null ? String(rowSelect.type) : '')
        setValue('status', normalizeStatus(rowSelect.status))

        // Precargar contrato real desde el backend porque Customer.contrato tiene @JsonIgnore
        let fechaInicio = rowSelect.contrato?.fechaInicio || ''
        let fechaFinal = rowSelect.contrato?.fechaFinal || ''
        let descripcionContrato = rowSelect.contrato?.descripcionContrato || ''

        try {
          const token = localStorage.getItem('AuthToken')
          if (token) {
            const contratoRes = await axios.get(
              `${process.env.NEXT_PUBLIC_API_URL}/customers/${rowSelect.id}/contracts`,
              {
                headers: {
                  'Content-Type': 'application/json',
                  Authorization: `Bearer ${token}`
                }
              }
            )
            if (contratoRes.data) {
              fechaInicio = contratoRes.data.fechaInicio || ''
              fechaFinal = contratoRes.data.fechaFinal || ''
              descripcionContrato = contratoRes.data.descripcionContrato || ''
            }
          }
        } catch {
          // El cliente puede no tener contrato creado todavía
        }

        setValue('fechaInicio', fechaInicio)
        setValue('fechaFinal', fechaFinal)
        setValue('descripcionContrato', descripcionContrato)
      } else {
        setId(null)
        reset({
          name: '',
          nit: '',
          phone: '',
          email: '',
          address: '',
          contact: '',
          position: '',
          type: '',
          fechaInicio: '',
          fechaFinal: '',
          descripcionContrato: '',
          status: '1'
        })
      }
    }

    if (open) {
      loadCustomerData()
    }
  }, [rowSelect, open, reset, setValue])

  return (
    <>
      <Dialog open={!!open} onClose={onClose} fullWidth maxWidth='md'>
        <DialogTitle>{id ? 'Editar cliente' : 'Agregar nuevo cliente'}</DialogTitle>
        <DialogContent>
          <Box component='form' onSubmit={handleSubmit(onSubmit)} noValidate sx={{ mt: 2 }}>
            <Grid container spacing={2}>
              <Grid item xs={12} sm={6}>
                <Controller
                  name='name'
                  control={control}
                  render={({ field }) => (
                    <TextField
                      {...field}
                      fullWidth
                      label='Nombre'
                      error={!!errors.name}
                      helperText={errors.name?.message}
                    />
                  )}
                />
              </Grid>
              <Grid item xs={12} sm={6}>
                <Controller
                  name='nit'
                  control={control}
                  render={({ field }) => (
                    <TextField
                      {...field}
                      fullWidth
                      label='NIT'
                      error={!!errors.nit}
                      helperText={errors.nit?.message}
                    />
                  )}
                />
              </Grid>
              <Grid item xs={12} sm={6}>
                <Controller
                  name='phone'
                  control={control}
                  render={({ field }) => (
                    <TextField
                      {...field}
                      fullWidth
                      label='Teléfono'
                      error={!!errors.phone}
                      helperText={errors.phone?.message}
                    />
                  )}
                />
              </Grid>
              <Grid item xs={12} sm={6}>
                <Controller
                  name='email'
                  control={control}
                  render={({ field }) => (
                    <TextField
                      {...field}
                      fullWidth
                      label='Email'
                      type='email'
                      error={!!errors.email}
                      helperText={errors.email?.message}
                    />
                  )}
                />
              </Grid>
              <Grid item xs={12}>
                <Controller
                  name='address'
                  control={control}
                  render={({ field }) => (
                    <TextField
                      {...field}
                      fullWidth
                      label='Dirección'
                      multiline
                      maxRows={4}
                      error={!!errors.address}
                      helperText={errors.address?.message}
                    />
                  )}
                />
              </Grid>
              <Grid item xs={12} sm={6}>
                <Controller
                  name='contact'
                  control={control}
                  render={({ field }) => (
                    <TextField
                      {...field}
                      fullWidth
                      label='Contacto'
                      error={!!errors.contact}
                      helperText={errors.contact?.message}
                    />
                  )}
                />
              </Grid>
              <Grid item xs={12} sm={6}>
                <Controller
                  name='position'
                  control={control}
                  render={({ field }) => (
                    <TextField
                      {...field}
                      fullWidth
                      label='Cargo'
                      error={!!errors.position}
                      helperText={errors.position?.message}
                    />
                  )}
                />
              </Grid>
              <Grid item xs={12} sm={6}>
                <Controller
                  name='type'
                  control={control}
                  render={({ field }) => (
                    <FormControl fullWidth error={!!errors.type}>
                      <InputLabel>Tipo</InputLabel>
                      <Select {...field} label='Tipo' value={field.value ?? ''}>
                        <MenuItem value=''>-- Selecciona tipo --</MenuItem>
                        <MenuItem value='1'>Externa</MenuItem>
                        <MenuItem value='0'>Interna</MenuItem>
                      </Select>
                      {errors.type && <FormHelperText>{errors.type.message}</FormHelperText>}
                    </FormControl>
                  )}
                />
              </Grid>
              <Grid item xs={12} sm={6}>
                <Controller
                  name='status'
                  control={control}
                  render={({ field }) => (
                    <FormControl fullWidth error={!!errors.status}>
                      <InputLabel>Estado</InputLabel>
                      <Select {...field} label='Estado' value={field.value ?? '1'}>
                        <MenuItem value='1'>Activo</MenuItem>
                        <MenuItem value='0'>Inactivo</MenuItem>
                      </Select>
                      {errors.status && <FormHelperText>{errors.status.message}</FormHelperText>}
                    </FormControl>
                  )}
                />
              </Grid>
              <Grid item xs={12} sm={12} className='my-4'>
                <Typography>
                  <b>Contrato</b>
                </Typography>
              </Grid>
              <Grid item xs={12} sm={6}>
                <Controller
                  name='fechaInicio'
                  control={control}
                  render={({ field }) => (
                    <TextField
                      {...field}
                      fullWidth
                      label='Fecha inicial'
                      type='date'
                      InputLabelProps={{ shrink: true }}
                      error={!!errors.fechaInicio}
                      helperText={errors.fechaInicio?.message}
                    />
                  )}
                />
              </Grid>
              <Grid item xs={12} sm={6}>
                <Controller
                  name='fechaFinal'
                  control={control}
                  render={({ field }) => (
                    <TextField
                      {...field}
                      fullWidth
                      label='Fecha final'
                      type='date'
                      InputLabelProps={{ shrink: true }}
                      error={!!errors.fechaFinal}
                      helperText={errors.fechaFinal?.message}
                    />
                  )}
                />
              </Grid>
              <Grid item xs={12}>
                <Divider className='my-4' />
                <Controller
                  name='descripcionContrato'
                  control={control}
                  render={({ field }) => (
                    <TextField
                      {...field}
                      fullWidth
                      label='Descripción'
                      multiline
                      maxRows={4}
                      error={!!errors.descripcionContrato}
                      helperText={errors.descripcionContrato?.message}
                    />
                  )}
                />
              </Grid>
            </Grid>
          </Box>
        </DialogContent>
        <DialogActions>
          <Button onClick={onClose} color='secondary' disabled={saving}>
            Cerrar
          </Button>
          <Button
            type='submit'
            variant='contained'
            color='primary'
            disabled={saving}
            onClick={handleSubmit(onSubmit)}
          >
            {saving ? 'Guardando...' : id ? 'Actualizar datos' : 'Guardar datos'}
          </Button>
        </DialogActions>
      </Dialog>

      {/* Diálogo MUI de confirmación al actualizar */}
      <Dialog open={confirmOpen} onClose={() => !saving && setConfirmOpen(false)} maxWidth='xs' fullWidth>
        <DialogTitle>Confirmar actualización</DialogTitle>
        <DialogContent>
          <DialogContentText>
            ¿Está seguro de actualizar toda la información del cliente <strong>{pendingData?.name}</strong> y su
            contrato?
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button variant='outlined' color='secondary' disabled={saving} onClick={() => setConfirmOpen(false)}>
            Cancelar
          </Button>
          <Button
            variant='contained'
            color='primary'
            disabled={saving}
            onClick={() => pendingData && executeSave(pendingData)}
          >
            {saving ? 'Guardando...' : 'Confirmar'}
          </Button>
        </DialogActions>
      </Dialog>

      <Snackbar
        open={feedback.open}
        autoHideDuration={5000}
        onClose={() => setFeedback(prev => ({ ...prev, open: false }))}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
      >
        <Alert
          onClose={() => setFeedback(prev => ({ ...prev, open: false }))}
          severity={feedback.severity}
          variant='filled'
          sx={{ width: '100%' }}
        >
          {feedback.message}
        </Alert>
      </Snackbar>
    </>
  )
}

export default ClienteForm
