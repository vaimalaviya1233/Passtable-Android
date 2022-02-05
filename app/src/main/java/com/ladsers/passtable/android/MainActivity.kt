package com.ladsers.passtable.android

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ladsers.passtable.android.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    private var newFile = false
    private var afterSelecting = false

    private lateinit var recentUri: MutableList<Uri>
    private lateinit var recentDate: MutableList<String>
    private lateinit var recentMps: MutableList<Boolean>
    private lateinit var adapter: RecentAdapter
    private lateinit var fileCreator: FileCreator
    private lateinit var msgDialog: MsgDialog

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        msgDialog = MsgDialog(this, window)

        binding.toolbar.root.title = getString(R.string.ui_ct_home)
        binding.toolbar.root.navigationIcon = ContextCompat.getDrawable(
            this,
            R.drawable.ic_logo
        )
        binding.toolbar.root.navigationIcon?.setTintList(null)
        setSupportActionBar(binding.toolbar.root)

        fileCreator =
            FileCreator(this, contentResolver, window) { openFileExplorer(true) }

        binding.btOpenFile.setOnClickListener { openFileExplorer(false) }
        binding.btNewFile.setOnClickListener { v -> fileCreator.askName(btView = v) }
        //binding.btAbout.setOnClickListener { }

        binding.rvRecent.layoutManager = LinearLayoutManager(
            this,
            LinearLayoutManager.VERTICAL,
            false
        )
        binding.rvRecent.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                binding.toolbar.root.elevation =
                    if (!recyclerView.canScrollVertically(-1)) 0f else 7f
            }
        })
        recentUri = mutableListOf()
        recentDate = mutableListOf()
        recentMps = mutableListOf()
        adapter = RecentAdapter(
                recentUri,
                recentDate,
                recentMps,
                this,
                { id, flag -> openRecentFile(id, flag) },
                { id, resCode -> popupAction(id, resCode) })
        binding.rvRecent.adapter = adapter
    }

    override fun onResume() {
        super.onResume()

        if (afterSelecting) afterSelecting = false
        else refreshRecentList()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.btSettings -> {
                val intent = Intent(this, SettingsActivity::class.java)
                startActivity(intent)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun openFileExplorer(newFile: Boolean) {
        this.newFile = newFile

        val intent = if (newFile) Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        else Intent(Intent.ACTION_OPEN_DOCUMENT)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val docsDir =
                "content://com.android.externalstorage.documents/document/primary:Documents".toUri()
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, docsDir)
        }
        intent.putExtra("android.content.extra.SHOW_ADVANCED", true)

        if (!newFile) {
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            intent.type = "application/octet-stream"
        }

        explorerResult.launch(intent)
    }

    private val explorerResult = registerForActivityResult(
        ActivityResultContracts
            .StartActivityForResult()
    ) { result ->
        afterSelecting = true

        if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult

        var uri = result.data?.data ?: return@registerForActivityResult //TODO: err msg
        val perms = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        contentResolver.takePersistableUriPermission(uri, perms)

        if (newFile) uri = fileCreator.createFile(uri)

        val intent = Intent(this, TableActivity::class.java)
        intent.putExtra("fileUri", uri)
        intent.putExtra("newFile", newFile)
        startActivity(intent)
    }

    private fun openRecentFile(id: Int, resCode: Int) {
        when (resCode) {
            0 -> { // ok
                val intent = Intent(this, TableActivity::class.java)
                intent.putExtra("fileUri", recentUri[id])
                intent.putExtra("newFile", false)
                startActivity(intent)
            }
            1 -> { // file from gdisk is lost
                refreshRecentList()
                Toast.makeText(
                    this, getString(R.string.ui_msg_recentFilesUpdated), Toast.LENGTH_SHORT
                ).show()
            }
            2 -> { // local file is lost
                msgDialog.create(
                    getString(R.string.dlg_title_cannotBeOpened),
                    getString(R.string.dlg_err_recentFileDoesNotExist)
                )
                msgDialog.addPositiveBtn(
                    getString(R.string.app_bt_ok),
                    R.drawable.ic_accept
                ) { removeFromRecentList(id) }
                msgDialog.addSkipAction { removeFromRecentList(id) }
                msgDialog.show()
            }
        }
    }

    private fun popupAction(id: Int, resCode: Int){
        when (resCode){
            1 -> { // remove from list
                removeFromRecentList(id)
            }
            2 -> { // forget password
                RecentFiles.forgetMpEncrypted(this, recentUri[id])
                recentMps[id] = false
                adapter.notifyItemChanged(id)
            }
        }
    }

    private fun refreshRecentList(){
        recentUri.clear()
        recentUri.addAll(RecentFiles.loadUri(this))
        recentDate.clear()
        recentDate.addAll(RecentFiles.loadDate(this))
        recentMps.clear()
        recentMps.addAll(RecentFiles.loadMpsEncrypted(this))
        adapter.notifyDataSetChanged()
    }

    private fun removeFromRecentList(id: Int){
        RecentFiles.remove(this, recentUri[id])
        recentUri.removeAt(id)
        recentDate.removeAt(id)
        recentMps.removeAt(id)
        adapter.notifyItemRemoved(id)
        adapter.notifyItemRangeChanged(id, adapter.itemCount)
    }
}