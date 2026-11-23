'use client'

// React Imports
import { useEffect, useState, useMemo } from 'react'

// Next Imports
import Link from 'next/link'

// MUI Imports
import Alert from '@mui/material/Alert'
import Card from '@mui/material/Card'
import CardHeader from '@mui/material/CardHeader'
import Button from '@mui/material/Button'
import Typography from '@mui/material/Typography'
import Chip from '@mui/material/Chip'
import Checkbox from '@mui/material/Checkbox'
import IconButton from '@mui/material/IconButton'
import Snackbar from '@mui/material/Snackbar'
import Tooltip from '@mui/material/Tooltip'

import TablePagination from '@mui/material/TablePagination'
import type { TextFieldProps } from '@mui/material/TextField'
import MenuItem from '@mui/material/MenuItem'

// Third-party Importss
import axios from 'axios'
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
// eslint-disable-next-line import/no-unresolved
import TablePaginationComponent from '@components/TablePaginationComponent'
import type { UsersType } from '@/types/apps/userType'

// Component Imports
import TableFilters from './TableFilters'
import ConfirmationDialog from '@/components/dialogs/ConfirmationDialog'

// eslint-disable-next-line import/no-unresolved
import CustomTextField from '@core/components/mui/TextField'

// Style Imports
// eslint-disable-next-line import/no-unresolved
import tableStyles from '@core/styles/table.module.css'

declare module '@tanstack/table-core' {
  interface FilterFns {
    fuzzy: FilterFn<unknown>
  }
  interface FilterMeta {
    itemRank: RankingInfo
  }
}

type UsersTypeWithAction = UsersType & {
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
const columnHelper = createColumnHelper<UsersTypeWithAction>()

const UsersListTable = ({ tableData }: { tableData?: UsersType[] }) => {
  // States
  const [addUserOpen, setAddUserOpen] = useState(false)
  const [rowSelection, setRowSelection] = useState({})
  const [data, setData] = useState<UsersType[]>(tableData || [])
  const [filteredData, setFilteredData] = useState<UsersType[]>(tableData || [])
  const [globalFilter, setGlobalFilter] = useState('')
  const [confirmOpen, setConfirmOpen] = useState(false)
  const [userToDelete, setUserToDelete] = useState<UsersType | null>(null)
  const [feedback, setFeedback] = useState<{
    open: boolean
    message: string
    severity: 'success' | 'error' | 'warning' | 'info'
  }>({
    open: false,
    message: '',
    severity: 'info'
  })

  useEffect(() => {
    setData(tableData || [])
    setFilteredData(tableData || [])
  }, [tableData])

  const handleOpenDeleteConfirm = (user: UsersType) => {
    setUserToDelete(user)
    setConfirmOpen(true)
  }

  const handleConfirmDelete = async () => {
    if (!userToDelete) return

    try {
      const token = localStorage.getItem('AuthToken')

      if (!token) {
        throw new Error('Token no disponible. Por favor, inicia sesión nuevamente.')
      }

      const response = await axios.delete(`${process.env.NEXT_PUBLIC_API_URL}/users/${userToDelete.id}`, {
        headers: {
          Authorization: `Bearer ${token}`
        }
      })

      const nextData = data.filter(item => item.id !== userToDelete.id)
      setData(nextData)
      setFilteredData(prev => prev.filter(item => item.id !== userToDelete.id))
      setConfirmOpen(false)
      setUserToDelete(null)
      setFeedback({
        open: true,
        message: response.data?.message || 'Usuario eliminado correctamente.',
        severity: 'success'
      })
    } catch (error: any) {
      console.error('Error al eliminar usuario:', error)
      const backendMessage =
        error?.response?.data?.message || error?.message || 'No se pudo eliminar el usuario.'
      setConfirmOpen(false)
      setUserToDelete(null)
      setFeedback({
        open: true,
        message: backendMessage,
        severity: error?.response?.status === 400 ? 'warning' : 'error'
      })
    }
  }

  const columns = useMemo<ColumnDef<UsersTypeWithAction, any>[]>(
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
      columnHelper.accessor('username', {
        header: 'Usuario',
        cell: ({ row }) => (
          <div className='flex items-center gap-4'>
            <div className='flex flex-col'>
              <Typography color='text.primary' className='font-medium'>
                {row.original.username}
              </Typography>
              <Typography variant='body2'>{row.original.username}</Typography>
            </div>
          </div>
        )
      }),
      columnHelper.accessor('roles', {
        header: 'Roles',
        cell: ({ row }) => (
          <div className='flex items-center gap-2'>
            {row.original.roles &&
              row.original.roles.map(role => (
                <Chip
                  key={role.id}
                  variant='outlined'
                  label={role.roleEnum}
                  color='primary' // Cambiar el color dinámicamente si es necesario
                  className='capitalize'
                />
              ))}
          </div>
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

      columnHelper.accessor('enabled', {
        header: 'Estado',
        cell: ({ row }) => (
          <div className='flex items-center gap-3'>
            <Chip
              variant='tonal'
              label={row.original.enabled ? 'Activo' : 'Inactivo'}
              size='small'
              color={row.original.enabled ? 'success' : 'error'} // Cambiar color dinámicamente
              className='capitalize'
            />
          </div>
        )
      }),
      columnHelper.accessor('action', {
        header: 'Action',
        cell: ({ row }) => (
          <div className='flex items-center'>
            <Tooltip title='Editar usuario'>
              <IconButton component={Link} href={'/accounts/user/form/' + row.original.id}>
                <i className='tabler-edit text-textSecondary' />
              </IconButton>
            </Tooltip>

            <Tooltip title='Eliminar usuario'>
              <IconButton onClick={() => handleOpenDeleteConfirm(row.original)}>
                <i className='tabler-trash text-textSecondary' />
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

  const table = useReactTable<UsersTypeWithAction>({
    data: filteredData as UsersType[],
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
            <Link href='/accounts/user/form' passHref>
              <Button variant='contained' startIcon={<i className='tabler-plus' />} className='max-sm:is-full'>
                Agregar usuario
              </Button>
            </Link>
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
          component={() => <TablePaginationComponent table={table as any} />}
          count={table.getFilteredRowModel().rows.length}
          rowsPerPage={table.getState().pagination.pageSize}
          page={table.getState().pagination.pageIndex}
          onPageChange={(_, page) => {
            table.setPageIndex(page)
          }}
        />
      </Card>

      <ConfirmationDialog
        open={confirmOpen}
        setOpen={setConfirmOpen}
        entitYName='Usuario'
        name={
          userToDelete
            ? `${userToDelete.username} (${userToDelete.roles?.map(r => r.roleEnum).join(', ') || 'Sin rol'})`
            : ''
        }
        onConfirmation={handleConfirmDelete}
      />

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

export default UsersListTable
