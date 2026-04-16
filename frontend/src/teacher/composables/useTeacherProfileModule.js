import { ref } from 'vue'
import { updateUser } from '../../api/client'

export function useTeacherProfileModule({ props, relayUpdateUser }) {
  const profileForm = ref({ username: props.currentUser.username || '', email: props.currentUser.email || '' })
  const editProfileVisible = ref(false)
  const editProfileForm = ref({ username: '', email: '', college: '' })
  const changePasswordVisible = ref(false)
  const profileMessage = ref('')
  const selectedCollege = ref('')

  const loadTeacherProfile = () => {
    const legacyKey = 'teacher_college'
    const key = `teacher_college_${props.currentUser?.id ?? ''}`
    const storedCollege = localStorage.getItem(key) || ''
    if (storedCollege) {
      selectedCollege.value = storedCollege
      return
    }
    const legacy = localStorage.getItem(legacyKey) || ''
    if (legacy) {
      selectedCollege.value = legacy
      try {
        localStorage.setItem(key, legacy)
        localStorage.removeItem(legacyKey)
      } catch (e) {}
      return
    }
    if (props.currentUser && props.currentUser.college) {
      selectedCollege.value = props.currentUser.college
    } else {
      selectedCollege.value = ''
    }
  }

  const openEditProfile = () => {
    editProfileForm.value.username = profileForm.value.username || props.currentUser.username || ''
    editProfileForm.value.email = profileForm.value.email || props.currentUser.email || ''
    editProfileForm.value.college = selectedCollege.value || ''
    editProfileVisible.value = true
  }

  const handleSaveProfile = async () => {
    profileForm.value.username = editProfileForm.value.username
    profileForm.value.email = editProfileForm.value.email
    selectedCollege.value = editProfileForm.value.college || ''
    try {
      const key = `teacher_college_${props.currentUser?.id ?? ''}`
      localStorage.setItem(key, selectedCollege.value || '')
      localStorage.removeItem('teacher_college')
    } catch (e) {}
    try {
      const payload = {
        userId: props.currentUser.id,
        username: profileForm.value.username,
        email: profileForm.value.email,
        college: selectedCollege.value || ''
      }
      const resp = await updateUser(payload)
      const updatedUser = resp?.data?.user
        ? resp.data.user
        : { ...props.currentUser, username: profileForm.value.username, email: profileForm.value.email }
      const enrichedUser = { ...updatedUser, college: selectedCollege.value || '' }
      try {
        localStorage.setItem('currentUser', JSON.stringify(enrichedUser))
      } catch (e) {}
      relayUpdateUser?.({
        username: enrichedUser.username,
        email: enrichedUser.email,
        college: enrichedUser.college,
        ...(enrichedUser.workId !== undefined && enrichedUser.workId !== null ? { workId: enrichedUser.workId } : {})
      })
      profileMessage.value = resp?.data?.message || '已更新用户信息'
    } catch (err) {
      const fallbackUser = {
        ...props.currentUser,
        username: profileForm.value.username,
        email: profileForm.value.email,
        college: selectedCollege.value || ''
      }
      try {
        localStorage.setItem('currentUser', JSON.stringify(fallbackUser))
      } catch (e) {}
      relayUpdateUser?.({
        username: fallbackUser.username,
        email: fallbackUser.email,
        college: fallbackUser.college,
        ...(fallbackUser.workId ? { workId: fallbackUser.workId } : {})
      })
      profileMessage.value = err?.response?.data?.message || '已更新本地信息（未同步服务器）'
    } finally {
      editProfileVisible.value = false
      setTimeout(() => (profileMessage.value = ''), 2500)
    }
  }

  const openPasswordPage = () => {
    changePasswordVisible.value = true
  }

  return {
    profileForm,
    editProfileVisible,
    editProfileForm,
    changePasswordVisible,
    profileMessage,
    selectedCollege,
    loadTeacherProfile,
    openEditProfile,
    handleSaveProfile,
    openPasswordPage
  }
}
