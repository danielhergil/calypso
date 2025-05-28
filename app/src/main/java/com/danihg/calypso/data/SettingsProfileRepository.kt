package com.danihg.calypso.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.SetOptions

/**
 * Modelo sencillo de perfil para listados.
 */
data class SettingsProfile(
    val id: String,
    val alias: String,
    val videoResolution: String,
    val videoFps: Int,
    val recordResolution: String,
    val codec: String,
    val connectionsCount: Int
)

/**
 * Repositorio para operaciones de SettingsProfile en Firestore.
 */
class SettingsProfileRepository {
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    /**
     * Callback para fetchProfiles
     */
    interface FetchCallback {
        fun onEmpty()
        fun onError(e: Exception)
        fun onLoaded(profiles: List<SettingsProfile>)
    }

    /**
     * Obtiene los perfiles de settings del usuario actual.
     */
    fun fetchProfiles(callback: FetchCallback) {
        val user = auth.currentUser
        if (user == null) {
            callback.onEmpty()
            return
        }
        firestore
            .collection("users")
            .document(user.uid)
            .collection("settings_profiles")
            .get()
            .addOnSuccessListener { snap ->
                if (snap.isEmpty) {
                    callback.onEmpty()
                } else {
                    val list = snap.documents.map { doc ->
                        val video = doc.get("videoSettings") as? Map<*,*> ?: emptyMap<String, Any>()
                        val record = doc.get("recordSettings") as? Map<*,*> ?: emptyMap<String, Any>()
                        val fps = (video["fps"] as? Number)?.toInt() ?: 0
                        val conns = doc.get("connections") as? List<*> ?: emptyList<Any>()
                        SettingsProfile(
                            id = doc.id,
                            alias = doc.getString("alias") ?: "",
                            videoResolution = video["resolution"] as? String ?: "",
                            videoFps = fps,
                            recordResolution = record["resolution"] as? String ?: "",
                            codec = video["codec"] as? String ?: "",
                            connectionsCount = conns.size
                        )
                    }
                    callback.onLoaded(list)
                }
            }
            .addOnFailureListener { e ->
                callback.onError(e)
            }
    }

    /**
     * Callback para createProfile
     */
    interface CreateCallback {
        fun onSuccess(docId: String)
        fun onFailure(e: Exception)
    }

    /**
     * Crea un nuevo perfil en Firestore bajo:
     * users/{uid}/settings_profiles
     */
    fun createProfile(
        profileData: Map<String, Any>,
        callback: CreateCallback
    ) {
        val user = auth.currentUser
        if (user == null) {
            callback.onFailure(IllegalStateException("User not logged in"))
            return
        }
        firestore
            .collection("users")
            .document(user.uid)
            .collection("settings_profiles")
            .add(profileData)
            .addOnSuccessListener { docRef: DocumentReference ->
                callback.onSuccess(docRef.id)
            }
            .addOnFailureListener { e ->
                callback.onFailure(e)
            }
    }

    /**
     * Callback para updateProfile
     */
    interface UpdateCallback {
        fun onSuccess()
        fun onFailure(e: Exception)
    }

    /**
     * Actualiza un perfil existente:
     * users/{uid}/settings_profiles/{profileId}
     */
    fun updateProfile(
        profileId: String,
        profileData: Map<String, Any>,
        callback: UpdateCallback
    ) {
        val user = auth.currentUser
        if (user == null) {
            callback.onFailure(IllegalStateException("User not logged in"))
            return
        }
        firestore
            .collection("users")
            .document(user.uid)
            .collection("settings_profiles")
            .document(profileId)
            .set(profileData, SetOptions.merge())
            .addOnSuccessListener {
                callback.onSuccess()
            }
            .addOnFailureListener { e ->
                callback.onFailure(e)
            }
    }


    /**
     * Callback para deleteProfile
     */
    interface DeleteCallback {
        fun onSuccess()
        fun onFailure(e: Exception)
    }

    /**
     * Elimina un perfil existente:
     * users/{uid}/settings_profiles/{profileId}
     */
    fun deleteProfile(
        profileId: String,
        callback: DeleteCallback
    ) {
        val user = auth.currentUser
        if (user == null) {
            callback.onFailure(IllegalStateException("User not logged in"))
            return
        }
        firestore
            .collection("users")
            .document(user.uid)
            .collection("settings_profiles")
            .document(profileId)
            .delete()
            .addOnSuccessListener {
                callback.onSuccess()
            }
            .addOnFailureListener { e ->
                callback.onFailure(e)
            }
    }

    /**
     * Obtiene un solo perfil por ID para rellenar el formulario.
     */
    fun fetchProfileById(
        profileId: String,
        callback: (data: Map<String, Any>?, error: Exception?) -> Unit
    ) {
        val user = auth.currentUser
        if (user == null) {
            callback(null, IllegalStateException("User not logged in"))
            return
        }
        firestore
            .collection("users")
            .document(user.uid)
            .collection("settings_profiles")
            .document(profileId)
            .get()
            .addOnSuccessListener { doc ->
                callback(doc.data as? Map<String, Any>, null)
            }
            .addOnFailureListener { e ->
                callback(null, e)
            }
    }
}
