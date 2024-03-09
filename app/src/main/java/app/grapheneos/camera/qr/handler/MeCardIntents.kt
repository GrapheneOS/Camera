package app.grapheneos.camera.qr.handler


import android.content.ContentValues
import android.content.Intent
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds
import android.provider.ContactsContract.CommonDataKinds.Nickname
import android.provider.ContactsContract.CommonDataKinds.Website
import android.provider.ContactsContract.Intents
import app.grapheneos.camera.qr.data.MeCard

private fun urlToContactField(url: String, type: Int = Website.TYPE_HOMEPAGE): ContentValues {
    return ContentValues().apply {
        put(ContactsContract.Data.MIMETYPE, Website.CONTENT_ITEM_TYPE)
        put(Website.TYPE, type)
        put(Website.URL, url)
    }
}

private fun nickNameToContactField(name: String, type: Int = Nickname.TYPE_DEFAULT): ContentValues {
    return ContentValues().apply {
        put(ContactsContract.Data.MIMETYPE, Nickname.CONTENT_ITEM_TYPE)
        put(Nickname.TYPE, type)
        put(Nickname.NAME, name)
    }
}

private fun birthdayToContactField(date: String): ContentValues {
    return ContentValues().apply {
        put(ContactsContract.Data.MIMETYPE, CommonDataKinds.Event.CONTENT_ITEM_TYPE)
        put(CommonDataKinds.Event.TYPE, CommonDataKinds.Event.TYPE_BIRTHDAY)
        put(CommonDataKinds.Event.START_DATE, date)
    }
}

private fun phoneticNameToContactField(name: String): ContentValues {
    return ContentValues().apply {
        put(ContactsContract.Data.MIMETYPE, CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
        put(CommonDataKinds.StructuredName.DISPLAY_NAME, name)
    }
}

fun MeCard.addToContact(): Intent {

    val data = ArrayList<ContentValues>().apply {
        if (url.isNotBlank()) add(urlToContactField(url))
        if (nickName.isNotBlank()) add(nickNameToContactField(nickName))
        if (birthDate.isNotBlank()) add(birthdayToContactField(birthDate))
        if (sound.isNotBlank()) add(phoneticNameToContactField(sound))
    }

    return Intent(Intents.Insert.ACTION).apply {
        type = ContactsContract.RawContacts.CONTENT_TYPE

        if (name.isNotBlank()) putExtra(Intents.Insert.NAME, name)
        if (email.isNotBlank()) putExtra(Intents.Insert.EMAIL, email)
        if (note.isNotBlank()) putExtra(Intents.Insert.NOTES, note)
        if (telephoneNumber.isNotBlank()) putExtra(Intents.Insert.PHONE, telephoneNumber)
        if (address.isNotBlank()) putExtra(Intents.Insert.POSTAL, address)
        if (data.isNotEmpty()) putParcelableArrayListExtra(Intents.Insert.DATA, data)
        if (telephoneNumberAv.isNotBlank()) {
            putExtra(Intents.Insert.SECONDARY_PHONE, telephoneNumberAv)
        }
    }
}
