// React Imports
import React from 'react'

// MUI Imports
import Button from '@mui/material/Button'
import Dialog from '@mui/material/Dialog'
import DialogTitle from '@mui/material/DialogTitle'
import DialogContent from '@mui/material/DialogContent'
import DialogActions from '@mui/material/DialogActions'
import DialogContentText from '@mui/material/DialogContentText'
import Typography from '@mui/material/Typography'

interface ConfirmationDialogProps {
  name: string
  entitYName: string
  open: boolean
  onConfirmation: (name: string) => void
  setOpen: (open: boolean) => void
}

const ConfirmationDialog: React.FC<ConfirmationDialogProps> = ({
  entitYName,
  name,
  open,
  onConfirmation,
  setOpen
}) => {
  const handleClose = () => setOpen(false)

  return (
    <Dialog
      open={open}
      onClose={handleClose}
      aria-labelledby='alert-dialog-title'
      aria-describedby='alert-dialog-description'
      closeAfterTransition={false}
      maxWidth='xs'
      fullWidth
    >
      <DialogTitle id='alert-dialog-title'>
        Eliminar {entitYName}
      </DialogTitle>
      <DialogContent>
        <DialogContentText id='alert-dialog-description'>
          ¿Está seguro de eliminar este elemento?
        </DialogContentText>
        {name && (
          <Typography variant='caption' color='text.secondary' sx={{ display: 'block', mt: 1, wordBreak: 'break-all' }}>
            {name}
          </Typography>
        )}
      </DialogContent>
      <DialogActions className='dialog-actions-dense'>
        <Button variant='outlined' color='secondary' onClick={handleClose}>
          Cancelar
        </Button>
        <Button variant='contained' color='error' onClick={() => onConfirmation(name)}>
          Eliminar
        </Button>
      </DialogActions>
    </Dialog>
  )
}

export default ConfirmationDialog
