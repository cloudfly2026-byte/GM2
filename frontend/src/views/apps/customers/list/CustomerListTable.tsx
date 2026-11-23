'use client'

// React Imports
import { useEffect, useState, useMemo } from 'react'

// MUI Imports
import Alert from '@mui/material/Alert'
import Card from '@mui/material/Card'
import CardHeader from '@mui/material/CardHeader'
import Button from '@mui/material/Button'
import Typography from '@mui/material/Typography'
import Chip from '@mui/material/Chip'
import Checkbox from '@mui/material/Checkbox'
import IconButton from '@mui/material/IconButton'
import InputAdornment from '@mui/material/InputAdornment'
import Dialog from '@mui/material/Dialog'
import DialogActions from '@mui/material/DialogActions'
import DialogContent from '@mui/material/DialogContent'
import DialogContentText from '@mui/material/DialogContentText'
import DialogTitle from '@mui/material/DialogTitle'
import Snackbar from '@mui/material/Snackbar'
import TextField from '@mui/material/TextField'
import Tooltip from '@mui/material/Tooltip'
import axios from 'axios'

import TablePagination from '@mui/material/TablePagination'
import type { TextFieldProps } from '@mui/material/TextField'
import MenuItem from '@mui/material/MenuItem'

// Third-party Importss
import classnames from 'classnames'
import { rankItem } from '@tanstack/match-sorter-utils'
import {
  createColumnHelper,
  flexRender,
  getCoreRowModel,
  useReactTable,
  getFilteredRowModel,
  getFacetedRowModel,
  getFacetedUniqueValues,
  getFacetedMinMaxValues,
  getPaginationRowModel,
  getSortedRowModel
} from '@tanstack/react-table'
import type { ColumnDef, FilterFn } from '@tanstack/react-table'
import type { RankingInfo } from '@tanstack/match-sorter-utils'

// Type Imports
import TablePaginationComponent from '@components/TablePaginationComponent'
import type { CustomersType } from '@/types/apps/customerType'

import ClienteForm from '@/components/dialogs/form-customer'

// Component Imports
import TableFilters from './TableFilters'

//import AddCustomerDrawer from './AddCustomerDrawer'

import CustomTextField from '@core/components/mui/TextField'

// Style Imports
import tableStyles from '@core/styles/table.module.css'

declare module '@tanstack/table-core' {
  interface FilterFns {
    fuzzy: FilterFn<unknown>
  }
  interface FilterMeta {
    itemRank: RankingInfo
  }
}

type CustomersTypeWithAction = CustomersType & {
  action?: string
}

const fuzzyFilter: FilterFn<any> = (row, columnId, value, addMeta) => {
  // Rank the item
  const itemRank = rankItem(row.getValue(columnId), value)

  // Store the itemRank info
  addMeta({
    itemRank
  })

  // Return if the item should be filtered in/out
  return itemRank.passed
}

const DebouncedInput = ({
  value: initialValue,
  onChange,
  debounce = 500,
  ...props
}: {
  value: string | number
  onChange: (value: string | number) => void
  debounce?: number
} & Omit<TextFieldProps, 'onChange'>) => {
  // States
  const [value, setValue] = useState(initialValue)

  useEffect(() => {
    setValue(initialValue)
  }, [initialValue])

  useEffect(() => {
    const timeout = setTimeout(() => {
      onChange(value)
    }, debounce)

    return () => clearTimeout(timeout)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [value])

  return <CustomTextField {...props} value={value} onChange={e => setValue(e.target.value)} />
}

// Column Definitions
const columnHelper = createColumnHelper<CustomersTypeWithAction>()

const CustomersListTable = ({ reload, tableData }: any) => {
  // States
  // const [addCustomerOpen, setAddCustomerOpen] = useState(false)

  const [loadForm, setOpenForm] = useState(false)

  const [rowSelection, setRowSelection] = useState<any>({
    name: '',
    nit: '',
    phone: '',
    email: '',
    address: '',
    contact: '',
    position: '',
    type: '',
    dateRegister: '',
    status: '1',
    contrato: {
      fechaInicio: '',
      fechaFinal: '',
      descripcionContrato: '',
      estado: 1
    }
  })

  const [data, setData] = useState<CustomersType[]>(tableData)
  const [filteredData, setFilteredData] = useState(data)
  const [globalFilter, setGlobalFilter] = useState('')

  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false)
  const [customerToDelete, setCustomerToDelete] = useState<any>(null)
  const [confirmPassword, setConfirmPassword] = useState('')
  const [showConfirmPassword, setShowConfirmPassword] = useState(false)
  const [deleteError, setDeleteError] = useState<string | null>(null)
  const [deleting, setDeleting] = useState(false)
  const [feedback, setFeedback] = useState<{
    open: boolean
    message: string
    severity: 'success' | 'error' | 'warning' | 'info'
  }>({
    open: false,
    message: '',
    severity: 'info'
  })

  const closeDeleteDialog = () => {
    if (deleting) return
    setDeleteDialogOpen(false)
    setCustomerToDelete(null)
    setConfirmPassword('')
    setShowConfirmPassword(false)
    setDeleteError(null)
  }

  const deleteCustomer = async (id: number | string, password: string) => {
    setDeleting(true)
    setDeleteError(null)
    try {
      const token = localStorage.getItem('AuthToken')
      if (!token) {
        throw new Error('Token no disponible. Por favor, inicia sesión nuevamente.')
      }

      const response = await axios.delete(`${process.env.NEXT_PUBLIC_API_URL}/customers/${id}`, {
        data: { password },
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${token}`
        }
      })

      setData(prev => prev.filter(item => item.id !== id))
      setFilteredData(prev => prev.filter(item => item.id !== id))
      if (reload) {
        reload(true)
      }

      setFeedback({
        open: true,
        message: response.data?.message || 'Cliente y toda su información relacionada eliminados correctamente.',
        severity: 'success'
      })

      setDeleteDialogOpen(false)
      setCustomerToDelete(null)
      setConfirmPassword('')
      setShowConfirmPassword(false)
    } catch (error: any) {
      console.error('Error deleting customer:', error)
      const backendMessage =
        error?.response?.data?.message || error?.message || 'No se pudo eliminar el cliente.'
      setDeleteError(backendMessage)
    } finally {
      setDeleting(false)
    }
  }

  const columns = useMemo<ColumnDef<CustomersTypeWithAction, any>[]>(
    () => [
      {
        id: 'select',
        header: ({ table }) => (
          <Checkbox
            {...{
              checked: table.getIsAllRowsSelected(),
              indeterminate: table.getIsSomeRowsSelected(),
              onChange: table.getToggleAllRowsSelectedHandler()
            }}
          />
        ),
        cell: ({ row }) => (
          <Checkbox
            {...{
              checked: row.getIsSelected(),
              disabled: !row.getCanSelect(),
              indeterminate: row.getIsSomeSelected(),
              onChange: row.getToggleSelectedHandler()
            }}
          />
        )
      },
      columnHelper.accessor('name', {
        header: 'Nombre',
        cell: ({ row }) => (
          <div className='flex items-center gap-4'>
            <div className='flex flex-col'>
              <Typography color='text.primary' className='font-medium'>
                {row.original.name}
              </Typography>
              <Typography variant='body2'>Nit: {row.original.nit}</Typography>
            </div>
          </div>
        )
      }),
      columnHelper.accessor('phone', {
        header: 'Telefono',

        cell: ({ row }) => (
          <Typography className='font-medium' color='text.primary'>
            {row.original.phone}
          </Typography>
        )
      }),
      columnHelper.accessor('email', {
        header: 'Email',

        cell: ({ row }) => (
          <Typography className='font-medium' color='text.primary'>
            {row.original.email}
          </Typography>
        )
      }),

      columnHelper.accessor('address', {
        header: 'Dirección',
        cell: ({ row }) => (
          <Typography className='font-medium' color='text.primary'>
            {row.original.address}
          </Typography>
        )
      }),

      columnHelper.accessor('dateRegister', {
        header: 'Agregado',
        cell: ({ row }) => (
          <Typography className='font-medium' color='text.primary'>
            {row.original.dateRegister}
          </Typography>
        )
      }),
      columnHelper.accessor('status', {
        header: 'Estado',
        cell: ({ row }) => (
          <div className='flex items-center gap-3'>
            <Chip
              variant='tonal'
              label={row.original.status ? 'Activo' : 'Inactivo'}
              size='small'
              color={row.original.status ? 'success' : 'error'} // Cambiar color dinámicamente
              className='capitalize'
            />
          </div>
        )
      }),
      columnHelper.accessor('action', {
        header: 'Action',
        cell: ({ row }) => (
          <div className='flex items-center'>
            <Tooltip title='Eliminar cliente'>
              <IconButton
                onClick={() => {
                  setCustomerToDelete(row.original)
                  setConfirmPassword('')
                  setDeleteError(null)
                  setDeleteDialogOpen(true)
                }}
              >
                <i className='tabler-trash text-textSecondary' />
              </IconButton>
            </Tooltip>
            <Tooltip title='Editar cliente'>
              <IconButton
                onClick={() => {
                  setRowSelection(row.original)
                  setOpenForm(true)
                }}
              >
                <i className='tabler-edit text-textSecondary' />
              </IconButton>
            </Tooltip>
          </div>
        ),
        enableSorting: false
      })
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [data, filteredData]
  )

  useEffect(() => {
    setData(tableData || [])
    setFilteredData(tableData || [])
  }, [tableData])

  const table = useReactTable({
    data: filteredData as CustomersType[],
    columns,
    filterFns: {
      fuzzy: fuzzyFilter
    },
    state: {
      rowSelection,
      globalFilter
    },
    initialState: {
      pagination: {
        pageSize: 10
      }
    },
    enableRowSelection: true, //enable row selection for all rows
    // enableRowSelection: row => row.original.age > 18, // or enable row selection conditionally per row
    globalFilterFn: fuzzyFilter,
    onRowSelectionChange: setRowSelection,
    getCoreRowModel: getCoreRowModel(),
    onGlobalFilterChange: setGlobalFilter,
    getFilteredRowModel: getFilteredRowModel(),
    getSortedRowModel: getSortedRowModel(),
    getPaginationRowModel: getPaginationRowModel(),
    getFacetedRowModel: getFacetedRowModel(),
    getFacetedUniqueValues: getFacetedUniqueValues(),
    getFacetedMinMaxValues: getFacetedMinMaxValues()
  })

  return (
    <>
      <Card>
        <CardHeader title='Filters' className='pbe-4' />
        <TableFilters setData={setFilteredData} tableData={data} />
        <div className='flex justify-between flex-col items-start md:flex-row md:items-center p-6 border-bs gap-4'>
          <CustomTextField
            select
            value={table.getState().pagination.pageSize}
            onChange={e => table.setPageSize(Number(e.target.value))}
            className='max-sm:is-full sm:is-[70px]'
          >
            <MenuItem value='10'>10</MenuItem>
            <MenuItem value='25'>25</MenuItem>
            <MenuItem value='50'>50</MenuItem>
          </CustomTextField>
          <div className='flex flex-col sm:flex-row max-sm:is-full items-start sm:items-center gap-4'>
            <DebouncedInput
              value={globalFilter ?? ''}
              onChange={value => setGlobalFilter(String(value))}
              placeholder='Buscar'
              className='max-sm:is-full'
            />
            {/* <Button
              color='secondary'
              variant='tonal'
              startIcon={<i className='tabler-upload' />}
              className='max-sm:is-full'
            >
              Exportar
            </Button>

            <Button
              color='secondary'
              variant='tonal'
              startIcon={<i className='tabler-download' />}
              className='max-sm:is-full'
            >
              Importar
            </Button> */}

            <Button
              onClick={() => {
                setOpenForm(true)
                setRowSelection({
                  name: '',
                  nit: '',
                  phone: '',
                  email: '',
                  address: '',
                  contact: '',
                  position: '',
                  type: '',
                  dateRegister: '',
                  status: '1',
                  contrato: {
                    estado: 1,
                    fechaInicio: '',
                    fechaFinal: '',
                    descripcionContrato: ''
                  }
                })
              }}
              variant='contained'
              startIcon={<i className='tabler-plus' />}
              className='max-sm:is-full'
            >
              Agregar cliente
            </Button>
          </div>
        </div>
        <div className='overflow-x-auto'>
          <table className={tableStyles.table}>
            <thead>
              {table.getHeaderGroups().map(headerGroup => (
                <tr key={headerGroup.id}>
                  {headerGroup.headers.map(header => (
                    <th key={header.id}>
                      {header.isPlaceholder ? null : (
                        <>
                          <div
                            className={classnames({
                              'flex items-center': header.column.getIsSorted(),
                              'cursor-pointer select-none': header.column.getCanSort()
                            })}
                            onClick={header.column.getToggleSortingHandler()}
                          >
                            {flexRender(header.column.columnDef.header, header.getContext())}
                            {{
                              asc: <i className='tabler-chevron-up text-xl' />,
                              desc: <i className='tabler-chevron-down text-xl' />
                            }[header.column.getIsSorted() as 'asc' | 'desc'] ?? null}
                          </div>
                        </>
                      )}
                    </th>
                  ))}
                </tr>
              ))}
            </thead>
            {table.getFilteredRowModel().rows.length === 0 ? (
              <tbody>
                <tr>
                  <td colSpan={table.getVisibleFlatColumns().length} className='text-center'>
                    No data available
                  </td>
                </tr>
              </tbody>
            ) : (
              <tbody>
                {table
                  .getRowModel()
                  .rows.slice(0, table.getState().pagination.pageSize)
                  .map(row => {
                    return (
                      <tr key={row.id} className={classnames({ selected: row.getIsSelected() })}>
                        {row.getVisibleCells().map(cell => (
                          <td key={cell.id}>{flexRender(cell.column.columnDef.cell, cell.getContext())}</td>
                        ))}
                      </tr>
                    )
                  })}
              </tbody>
            )}
          </table>
        </div>
        <TablePagination
          component={() => <TablePaginationComponent table={table} />}
          count={table.getFilteredRowModel().rows.length}
          rowsPerPage={table.getState().pagination.pageSize}
          page={table.getState().pagination.pageIndex}
          onPageChange={(_, page) => {
            table.setPageIndex(page)
          }}
        />
      </Card>
      {/* <AddCustomerDrawer
        open={addCustomerOpen}
        handleClose={() => setAddCustomerOpen(!addCustomerOpen)}
        customerData={data}
        setData={setData}
      /> */}

      <ClienteForm
        open={loadForm}
        onClose={() => {
          setOpenForm(false)
          reload(true)
          setRowSelection({
            id: null,
            name: '',
            nit: '',
            phone: '',
            email: '',
            address: '',
            contact: '',
            position: '',
            type: '',
            dateRegister: '',
            status: '1',
            contrato: {
              fechaInicio: '',
              fechaFinal: '',
              descripcionContrato: '',
              estado: 1
            }
          })
        }}
        setOpen={() => setOpenForm(true)}
        rowSelect={rowSelection}
      />

      <Dialog
        open={deleteDialogOpen}
        onClose={closeDeleteDialog}
        aria-labelledby='alert-dialog-title'
        aria-describedby='alert-dialog-description'
        maxWidth='sm'
        fullWidth
      >
        <DialogTitle id='alert-dialog-title'>Confirmar eliminación definitiva de cliente</DialogTitle>
        <DialogContent>
          <Alert severity='warning' sx={{ mb: 3, mt: 1 }}>
            Esta acción eliminará al cliente <strong>{customerToDelete?.name}</strong> y{' '}
            <strong>toda su información relacionada</strong> (usuarios, equipos, contratos, solicitudes, reportes,
            cronogramas y documentos). Esta acción no se puede deshacer.
          </Alert>
          <DialogContentText id='alert-dialog-description' sx={{ mb: 2 }}>
            Por seguridad, ingresa nuevamente tu contraseña de usuario para confirmar la eliminación:
          </DialogContentText>
          <TextField
            fullWidth
            label='Contraseña actual'
            type={showConfirmPassword ? 'text' : 'password'}
            value={confirmPassword}
            onChange={e => {
              setConfirmPassword(e.target.value)
              if (deleteError) setDeleteError(null)
            }}
            disabled={deleting}
            autoFocus
            InputProps={{
              endAdornment: (
                <InputAdornment position='end'>
                  <Button size='small' onClick={() => setShowConfirmPassword(prev => !prev)}>
                    {showConfirmPassword ? 'Ocultar' : 'Mostrar'}
                  </Button>
                </InputAdornment>
              )
            }}
          />
          {deleteError && (
            <Alert severity='error' sx={{ mt: 2 }}>
              {deleteError}
            </Alert>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={closeDeleteDialog} color='secondary' variant='outlined' disabled={deleting}>
            Cancelar
          </Button>
          <Button
            onClick={async () => {
              if (customerToDelete && confirmPassword.trim()) {
                await deleteCustomer(customerToDelete.id, confirmPassword)
              }
            }}
            color='error'
            variant='contained'
            disabled={deleting || !confirmPassword.trim()}
          >
            {deleting ? 'Eliminando...' : 'Eliminar definitivamente'}
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

export default CustomersListTable
