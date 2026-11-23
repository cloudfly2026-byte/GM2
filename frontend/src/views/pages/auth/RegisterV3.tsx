import { useState, useEffect } from 'react'

import { useRouter } from 'next/navigation'

import { useForm, Controller } from 'react-hook-form'
import * as yup from 'yup'
import { yupResolver } from '@hookform/resolvers/yup'
import Alert from '@mui/material/Alert'
import Dialog from '@mui/material/Dialog'
import DialogActions from '@mui/material/DialogActions'
import DialogContent from '@mui/material/DialogContent'
import DialogContentText from '@mui/material/DialogContentText'
import DialogTitle from '@mui/material/DialogTitle'
import Snackbar from '@mui/material/Snackbar'
import Typography from '@mui/material/Typography'
import IconButton from '@mui/material/IconButton'
import InputAdornment from '@mui/material/InputAdornment'
import Button from '@mui/material/Button'
import Grid from '@mui/material/Grid'
import Card from '@mui/material/Card'
import CardContent from '@mui/material/CardContent'
import axios from 'axios'
import dotenv from "dotenv";
import { MenuItem } from '@mui/material'

import CustomTextField from '@core/components/mui/TextField'
import { AuthManager } from '@/utils/authManager'
import { userMethods } from '@/utils/userMethods'

const RegisterV3 = ({ id }: { id: string }) => {
  const isEditing = Boolean(id && id !== '' && id !== '0')

  // States
  const [isPasswordShown, setIsPasswordShown] = useState(false)
  const [isConfirmPasswordShown, setIsConfirmPasswordShown] = useState(false)
  const [customersList, setCustomersList] = useState<any[]>([])
  const [roleList, setRoleList] = useState<any[]>([])
  const [userData, setUserData] = useState<any>(null)
  const [confirmSaveOpen, setConfirmSaveOpen] = useState(false)
  const [pendingSubmitData, setPendingSubmitData] = useState<any>(null)
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

  const router = useRouter()

  const fetchOptions = async () => {
    try {
      const token = localStorage.getItem('AuthToken')

      if (!token) {
        throw new Error('Token no disponible. Por favor, inicia sesión nuevamente.')
      }

      const [customersRes, rolesRes] = await Promise.all([
        axios.get(`${process.env.NEXT_PUBLIC_API_URL}/customers`, {
          headers: {
            'Content-Type': 'application/json',
            Authorization: `Bearer ${token}`
          }
        }),
        axios.get(`${process.env.NEXT_PUBLIC_API_URL}/roles`, {
          headers: {
            'Content-Type': 'application/json',
            Authorization: `Bearer ${token}`
          }
        })
      ])

      setCustomersList(customersRes.data)
      setRoleList(rolesRes.data)

      return true
    } catch (error) {
      console.error('Error al obtener datos:', error)

      return false
    }
  }

  // Cargar los datos del usuario si el ID existe

  useEffect(() => {
    console.log('load role admin', userMethods.isRole('SUPERADMIN'))
    // Cargar las opciones de clientes y roles
    fetchOptions()
  }, [])

  useEffect(() => {
    const fetchUserData = async () => {
      if (isEditing) {
        try {
          const token = localStorage.getItem('AuthToken')

          if (!token) {
            throw new Error('Token no disponible. Por favor, inicia sesión nuevamente.')
          }

          const response = await axios.get(`${process.env.NEXT_PUBLIC_API_URL}/users/${id}`, {
            headers: {
              'Content-Type': 'application/json',
              Authorization: `Bearer ${token}`
            }
          })

          const userLogued = userMethods.getUserLogin()
          const resolvedCustomerId = userMethods.isRole('ADMIN')
            ? userLogued?.customer?.id
            : response.data.customer?.id

          const normalizedUser = {
            ...response.data,
            customer: resolvedCustomerId ? { id: resolvedCustomerId } : null
          }

          setUserData(normalizedUser)
          setValue('customer', resolvedCustomerId ? String(resolvedCustomerId) : '')
          setValue('role', response.data.roles && response.data.roles.length > 0 ? String(response.data.roles[0].id) : '')
          setValue('nombres', response.data.nombres || '')
          setValue('apellidos', response.data.apellidos || '')
          setValue('username', response.data.username || '')
          setValue('email', response.data.email || '')
          setValue('password', '')
          setValue('confirmPassword', '')
        } catch (error) {
          console.error('Error al cargar datos del usuario:', error)
          setFeedback({
            open: true,
            message: 'No se pudieron cargar los datos del usuario.',
            severity: 'error'
          })
        }
      }
    }

    if (isEditing && customersList.length > 0 && roleList.length > 0) {
      fetchUserData()
    }
  }, [customersList, roleList, isEditing, id])

  // Validación con yup diferenciada entre creación y actualización
  const schema = yup.object().shape({
    customer: userMethods.isRole('ADMIN') ? yup.string().notRequired() : yup.string().required('Cliente es requerido'),
    role: yup.string().required('Rol es requerido'),
    nombres: yup.string().required('Los nombres son obligatorios'),
    apellidos: yup.string().required('Los apellidos son obligatorios'),
    username: isEditing
      ? yup.string().notRequired()
      : yup
          .string()
          .required('El nombre de usuario es obligatorio')
          .min(8, 'El nombre de usuario debe tener al menos 8 caracteres')
          .matches(/^[a-zA-Z0-9_]+$/, 'Solo se permiten letras, números y guion bajo (_). Sin espacios ni caracteres especiales')
          .test('username-exists', 'El nombre de usuario ya está en uso', async value => {
            if (!value) return false

            try {
              const response = await AuthManager.validateUsername({ username: value })

              return response.isAvailable
            } catch {
              return false
            }
          }),
    email: isEditing
      ? yup.string().notRequired()
      : yup
          .string()
          .email('El correo electrónico no tiene un formato válido')
          .required('El correo electrónico es obligatorio')
          .test('email-exists', 'El correo electrónico ya está en uso', async value => {
            if (!value) return false

            try {
              const response = await AuthManager.validateEmail({ email: value })

              return response.isAvailable
            } catch {
              return false
            }
          }),
    password: isEditing
      ? yup
          .string()
          .transform(val => (val === '' ? undefined : val))
          .notRequired()
          .min(8, 'La contraseña debe tener al menos 8 caracteres')
          .matches(/[a-z]/, 'Debe contener al menos una letra minúscula')
          .matches(/[A-Z]/, 'Debe contener al menos una letra mayúscula')
          .matches(/[0-9]/, 'Debe contener al menos un número')
          .matches(/[@$!%*?&]/, 'Debe contener al menos un carácter especial')
      : yup
          .string()
          .min(8, 'La contraseña debe tener al menos 8 caracteres')
          .matches(/[a-z]/, 'Debe contener al menos una letra minúscula')
          .matches(/[A-Z]/, 'Debe contener al menos una letra mayúscula')
          .matches(/[0-9]/, 'Debe contener al menos un número')
          .matches(/[@$!%*?&]/, 'Debe contener al menos un carácter especial')
          .required('La contraseña es obligatoria'),
    confirmPassword: isEditing
      ? yup.string().when('password', {
          is: (val: string | undefined) => Boolean(val && val.length > 0),
          then: s => s.oneOf([yup.ref('password')], 'Las contraseñas deben coincidir').required('Confirme la nueva contraseña'),
          otherwise: s => s.notRequired()
        })
      : yup
          .string()
          .oneOf([yup.ref('password')], 'Las contraseñas deben coincidir')
          .required('La confirmación de la contraseña es obligatoria')
  })

  // Hook form con yup
  const {
    control,
    handleSubmit,
    setValue,
    formState: { errors, isSubmitting }
  } = useForm({
    resolver: yupResolver(schema),
    defaultValues: {
      customer: '',
      role: '',
      nombres: '',
      apellidos: '',
      username: '',
      email: '',
      password: '',
      confirmPassword: ''
    }
  })

  const executeSave = async (data: any) => {
    setSaving(true)
    try {
      const token = localStorage.getItem('AuthToken')

      if (!token) {
        throw new Error('Token no disponible. Por favor, inicia sesión nuevamente.')
      }

      const userDataS = {
        id: isEditing ? Number(id) : 0,
        customer: userMethods.isRole('ADMIN')
          ? String(userMethods.getUserLogin()?.customer?.id ?? '')
          : String(data.customer || userData?.customer?.id || ''),
        role: String(data.role || userData?.roles?.[0]?.id || ''),
        nombres: data.nombres,
        apellidos: data.apellidos,
        username: isEditing ? userData?.username || data.username : data.username,
        email: isEditing ? userData?.email || data.email : data.email,
        password: data.password || '',
        confirmPassword: data.confirmPassword || ''
      }

      const apiUrl = `${process.env.NEXT_PUBLIC_API_URL}/users/save`

      const response = await axios({
        method: 'post',
        url: apiUrl,
        data: userDataS,
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${token}`
        }
      })

      if (response.data.result === 'success') {
        setFeedback({
          open: true,
          message: response.data.message || (isEditing ? 'Usuario actualizado correctamente.' : 'Usuario registrado correctamente.'),
          severity: 'success'
        })
        setTimeout(() => {
          router.push('/accounts/user/list')
        }, 900)
      } else {
        setFeedback({
          open: true,
          message: response.data.message || 'No se pudo guardar el usuario.',
          severity: 'error'
        })
      }
    } catch (error: any) {
      console.error('Error al registrar o actualizar el usuario:', error)
      const backendMessage =
        error?.response?.data?.message || error?.message || 'Error al guardar el usuario.'
      setFeedback({
        open: true,
        message: backendMessage,
        severity: error?.response?.status === 400 ? 'warning' : 'error'
      })
    } finally {
      setSaving(false)
      setConfirmSaveOpen(false)
      setPendingSubmitData(null)
    }
  }

  const onSubmit = async (data: any) => {
    if (isEditing) {
      setPendingSubmitData(data)
      setConfirmSaveOpen(true)
    } else {
      await executeSave(data)
    }
  }

  return (
    <div className='w-full'>
      <Card>
        <CardContent>
          <Typography variant='h4' className='mb-6'>Datos de usuario</Typography>
          {customersList.length > 0 && roleList.length > 0 && (
            <form onSubmit={handleSubmit(onSubmit)}>
              <Grid container spacing={5}>
                {/* Cliente */}
                {userMethods.isRole('SUPERADMIN') && (
                  <Grid item xs={12} md={6}>
                    <Controller
                      name='customer'
                      control={control}
                      render={({ field }) => (
                        <CustomTextField
                          {...field}
                          select
                          fullWidth
                          value={field.value ?? ''}
                          onChange={e => {
                            field.onChange(e.target.value)
                            setUserData((prev: any) => ({ ...prev, customer: { id: e.target.value } }))
                          }}
                          label='Cliente'
                          error={Boolean(errors.customer)}
                          helperText={errors.customer?.message}
                        >
                          {customersList.map(item => (
                            <MenuItem key={item.id} value={String(item.id)}>
                              {item.name}
                            </MenuItem>
                          ))}
                        </CustomTextField>
                      )}
                    />
                  </Grid>
                )}

                {/* Rol */}
                <Grid item xs={12} md={6}>
                  <Controller
                    name='role'
                    control={control}
                    render={({ field }) => (
                      <CustomTextField
                        {...field}
                        select
                        fullWidth
                        value={field.value ?? ''}
                        onChange={e => {
                          field.onChange(e.target.value)
                          setUserData((prev: any) => ({
                            ...prev,
                            roles: roleList.filter(item => String(item.id) === String(e.target.value))
                          }))
                        }}
                        label='Rol'
                        error={Boolean(errors.role)}
                        helperText={errors.role?.message}
                      >
                        {roleList.map((item: any) => {
                          if (
                            userMethods.isRole('SUPERADMIN') ||
                            (userMethods.isRole('ADMIN') && item.roleEnum != 'SUPERADMIN' && item.roleEnum != 'BIOMEDICAL')
                          ) {
                            return (
                              <MenuItem key={item.id} value={String(item.id)}>
                                {item.roleEnum}
                              </MenuItem>
                            )
                          }
                        })}
                      </CustomTextField>
                    )}
                  />
                </Grid>

                {/* Nombres */}
                <Grid item xs={12} md={6}>
                  <Controller
                    name='nombres'
                    control={control}
                    render={({ field }) => (
                      <CustomTextField
                        {...field}
                        fullWidth
                        label='Nombres'
                        placeholder='Ingrese los nombres'
                        error={Boolean(errors.nombres)}
                        helperText={errors.nombres?.message}
                      />
                    )}
                  />
                </Grid>

                {/* Apellidos */}
                <Grid item xs={12} md={6}>
                  <Controller
                    name='apellidos'
                    control={control}
                    render={({ field }) => (
                      <CustomTextField
                        {...field}
                        fullWidth
                        label='Apellidos'
                        placeholder='Ingrese los apellidos'
                        error={Boolean(errors.apellidos)}
                        helperText={errors.apellidos?.message}
                      />
                    )}
                  />
                </Grid>

                {/* Nombre de usuario */}
                <Grid item xs={12} md={6}>
                  <Controller
                    name='username'
                    control={control}
                    render={({ field }) => (
                      <CustomTextField
                        {...field}
                        fullWidth
                        disabled={isEditing}
                        label='Nombre de usuario'
                        placeholder='Mínimo 8 caracteres, solo letras, números y _'
                        error={Boolean(errors.username)}
                        helperText={
                          errors.username?.message ||
                          (isEditing ? 'El nombre de usuario no se puede modificar' : undefined)
                        }
                      />
                    )}
                  />
                </Grid>

                {/* Correo electrónico */}
                <Grid item xs={12} md={6}>
                  <Controller
                    name='email'
                    control={control}
                    render={({ field }) => (
                      <CustomTextField
                        {...field}
                        fullWidth
                        disabled={isEditing}
                        label='Correo electrónico'
                        type='email'
                        placeholder='ejemplo@correo.com'
                        error={Boolean(errors.email)}
                        helperText={
                          errors.email?.message ||
                          (isEditing ? 'El correo electrónico no se puede modificar' : undefined)
                        }
                      />
                    )}
                  />
                </Grid>

                {/* Contraseña */}
                <Grid item xs={12} md={6}>
                  <Controller
                    name='password'
                    control={control}
                    render={({ field }) => (
                      <CustomTextField
                        {...field}
                        fullWidth
                        label={isEditing ? 'Nueva contraseña (opcional)' : 'Contraseña'}
                        type={isPasswordShown ? 'text' : 'password'}
                        error={Boolean(errors.password)}
                        helperText={
                          errors.password?.message ||
                          (isEditing ? 'Déjalo vacío para mantener la contraseña actual' : undefined)
                        }
                        InputProps={{
                          endAdornment: (
                            <InputAdornment position='end'>
                              <IconButton onClick={() => setIsPasswordShown(!isPasswordShown)}>
                                {isPasswordShown ? 'Ocultar' : 'Mostrar'}
                              </IconButton>
                            </InputAdornment>
                          )
                        }}
                      />
                    )}
                  />
                </Grid>

                {/* Confirmar contraseña */}
                <Grid item xs={12} md={6}>
                  <Controller
                    name='confirmPassword'
                    control={control}
                    render={({ field }) => (
                      <CustomTextField
                        {...field}
                        fullWidth
                        label={isEditing ? 'Confirmar nueva contraseña' : 'Confirmar contraseña'}
                        type={isConfirmPasswordShown ? 'text' : 'password'}
                        error={Boolean(errors.confirmPassword)}
                        helperText={errors.confirmPassword?.message}
                        InputProps={{
                          endAdornment: (
                            <InputAdornment position='end'>
                              <IconButton onClick={() => setIsConfirmPasswordShown(!isConfirmPasswordShown)}>
                                {isConfirmPasswordShown ? 'Ocultar' : 'Mostrar'}
                              </IconButton>
                            </InputAdornment>
                          )
                        }}
                      />
                    )}
                  />
                </Grid>

                {/* Botón de envío */}
                <Grid item xs={12} className='flex gap-4'>
                  <Button
                    fullWidth
                    variant='contained'
                    color='primary'
                    type='submit'
                    disabled={isSubmitting || saving}
                    size='large'
                  >
                    {isEditing ? 'Actualizar' : 'Registrar'}
                  </Button>
                  <Button
                    variant='outlined'
                    color='secondary'
                    size='large'
                    onClick={() => router.push('/accounts/user/list')}
                  >
                    Cancelar
                  </Button>
                </Grid>
              </Grid>
            </form>
          )}
        </CardContent>
      </Card>

      {/* Diálogo de confirmación MUI para actualización */}
      <Dialog
        open={confirmSaveOpen}
        onClose={() => !saving && setConfirmSaveOpen(false)}
        maxWidth='xs'
        fullWidth
      >
        <DialogTitle>Confirmar actualización</DialogTitle>
        <DialogContent>
          <DialogContentText>
            ¿Está seguro de actualizar la información del usuario <strong>{userData?.username}</strong>?
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button variant='outlined' color='secondary' disabled={saving} onClick={() => setConfirmSaveOpen(false)}>
            Cancelar
          </Button>
          <Button
            variant='contained'
            color='primary'
            disabled={saving}
            onClick={() => pendingSubmitData && executeSave(pendingSubmitData)}
          >
            {saving ? 'Guardando...' : 'Confirmar'}
          </Button>
        </DialogActions>
      </Dialog>

      {/* Notificaciones MUI */}
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
    </div>
  )
}

export default RegisterV3
