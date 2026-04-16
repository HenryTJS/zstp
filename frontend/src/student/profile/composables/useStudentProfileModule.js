import { ref } from 'vue'
import { updateUser } from '../../../api/client'

export function useStudentProfileModule({
  currentUserRef,
  profileFormRef,
  profileMessageRef,
  selectedMajor1Ref,
  selectedMajor2Ref,
  selectedMajor3Ref,
  majorLevel2Ref,
  majorLevel3Ref,
  loadMajorLevel2,
  loadMajorLevel3,
  persistStudentState,
  relayUpdateUser
}) {
  const editProfileVisible = ref(false)
  const editProfileForm = ref({ username: '', email: '', major1: '', major2: '', major3: '' })
  const changePasswordVisible = ref(false)

  const openEditProfile = () => {
    editProfileForm.value.username = currentUserRef.value?.username || profileFormRef.value.username || ''
    editProfileForm.value.email = currentUserRef.value?.email || profileFormRef.value.email || ''
    editProfileForm.value.major1 = selectedMajor1Ref.value || ''
    editProfileForm.value.major2 = selectedMajor2Ref.value || ''
    editProfileForm.value.major3 = selectedMajor3Ref.value || ''
    editProfileVisible.value = true
  }

  const handleSaveProfile = async () => {
    profileFormRef.value.username = editProfileForm.value.username
    profileFormRef.value.email = editProfileForm.value.email

    selectedMajor1Ref.value = editProfileForm.value.major1 || ''
    if (selectedMajor1Ref.value) {
      await loadMajorLevel2(selectedMajor1Ref.value)
    } else {
      majorLevel2Ref.value = []
      majorLevel3Ref.value = []
    }
    selectedMajor2Ref.value = editProfileForm.value.major2 || ''
    if (selectedMajor2Ref.value) {
      await loadMajorLevel3(selectedMajor2Ref.value)
    } else {
      majorLevel3Ref.value = []
    }
    selectedMajor3Ref.value = editProfileForm.value.major3 || ''
    await persistStudentState(true)

    try {
      const payload = {
        userId: currentUserRef.value?.id,
        username: editProfileForm.value.username,
        email: editProfileForm.value.email
      }
      const resp = await updateUser(payload)
      editProfileVisible.value = false
      profileMessageRef.value = resp?.data?.message || '已更新用户信息'
      const updatedUser = resp?.data?.user
        ? resp.data.user
        : { ...currentUserRef.value, username: profileFormRef.value.username, email: profileFormRef.value.email }
      try { localStorage.setItem('currentUser', JSON.stringify(updatedUser)) } catch {}
      relayUpdateUser?.({
        username: updatedUser.username,
        email: updatedUser.email,
        ...(updatedUser.workId !== undefined && updatedUser.workId !== null ? { workId: updatedUser.workId } : {})
      })
    } catch (err) {
      editProfileVisible.value = false
      profileMessageRef.value = err?.response?.data?.message || '同步用户信息到服务器失败，请稍后重试。'
      const fallbackUser = { ...currentUserRef.value, username: profileFormRef.value.username, email: profileFormRef.value.email }
      try { localStorage.setItem('currentUser', JSON.stringify(fallbackUser)) } catch {}
      relayUpdateUser?.({
        username: profileFormRef.value.username,
        email: profileFormRef.value.email,
        ...(currentUserRef.value?.workId ? { workId: currentUserRef.value.workId } : {})
      })
    }
  }

  const openPasswordPage = () => {
    changePasswordVisible.value = true
  }

  return {
    editProfileVisible,
    editProfileForm,
    changePasswordVisible,
    openEditProfile,
    handleSaveProfile,
    openPasswordPage
  }
}

