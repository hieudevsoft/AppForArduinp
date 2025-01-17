package com.devapp.appforarduino.ui.activities

import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewTreeObserver
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.afollestad.dragselectrecyclerview.DragSelectReceiver
import com.afollestad.dragselectrecyclerview.DragSelectTouchListener
import com.afollestad.dragselectrecyclerview.Mode
import com.devapp.appforarduino.R
import com.devapp.appforarduino.data.db.LocalDataBase
import com.devapp.appforarduino.data.model.PixelData
import com.devapp.appforarduino.data.model.PixelDataTable
import com.devapp.appforarduino.databinding.ActivityLaunchPadBinding
import com.devapp.appforarduino.domain.app_repository.AppRepository
import com.devapp.appforarduino.domain.app_repository.AppRepositoryImpl
import com.devapp.appforarduino.domain.firebase_data_source.FireBaseRepository
import com.devapp.appforarduino.domain.firebase_data_source.FireBaseRepositoryImpl
import com.devapp.appforarduino.domain.firebase_data_source.FireBaseService
import com.devapp.appforarduino.domain.local_data_source.LocalDataRepository
import com.devapp.appforarduino.domain.local_data_source.LocalDataRepositoryImpl
import com.devapp.appforarduino.domain.usecases.*
import com.devapp.appforarduino.ui.adapter.LaunchPadAdapter
import com.devapp.appforarduino.ui.viewmodels.LaunchPadViewModel
import com.devapp.appforarduino.ui.viewmodels.LaunchPadViewModelFactory
import com.devapp.appforarduino.util.ColorSheetSingleTon
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch


class LaunchPadActivity : AppCompatActivity() {
		companion object {
				val ACTION_RESET_LAUNCHPAD = "ACTION_RESET_LAUNCHPAD"
				val TAG = "LaunchPadActivity"
		}

		private lateinit var binding: ActivityLaunchPadBinding
		private lateinit var launchPadAdapter: LaunchPadAdapter
		private val column = 64
		private val row = 32
		private var colorSelected = "#ff77a9"
		private val colorUnSelected = "#000000"
		private lateinit var myReceiver: MyReceiver
		private lateinit var listPixel: List<PixelData>


		private lateinit var firebaseService: FireBaseService
		private lateinit var firebaseRepository: FireBaseRepository
		private lateinit var localDataBase: LocalDataBase
		private lateinit var localDataRepository: LocalDataRepository
		private lateinit var appRepository: AppRepository
		private lateinit var updateLaunchPadUseCase: UpdateLaunchPadUseCase
		private lateinit var updateOptionUseCase: UpdateOptionUseCase
		private lateinit var saveLaunchPadUseCase: SaveLaunchPadUseCase
		private lateinit var launchPadViewModel: LaunchPadViewModel
		private lateinit var getCheckIfLaunchPadExists: CheckIfLaunchPadExists
		private lateinit var dialog: Dialog


		override fun onCreate(savedInstanceState: Bundle?) {
				super.onCreate(savedInstanceState)
				binding = ActivityLaunchPadBinding.inflate(layoutInflater)
				setup()
				setUpViewModel()
				setContentView(binding.root)
				binding.btnChooseColor.setOnClickListener {
						openBottomSheetColor()
				}
				binding.btnSaveLocal.setOnClickListener {
						openDialogSessionName()
				}
				binding.btnPushFireBase.setOnClickListener {
						updateLaunchPadToFireBase()
				}

		}

		private fun updateLaunchPadToFireBase() {
				launchPadViewModel.updateLaunchPadToFireBase(launchPadAdapter.listPixel)
				lifecycleScope.launchWhenStarted {
						launchPadViewModel.updateState.collect {
								when (it) {
										is LaunchPadViewModel.UpdateState.Loading -> {
												binding.loadingDots.visibility = View.VISIBLE
										}
										is LaunchPadViewModel.UpdateState.Error -> {
												binding.loadingDots.visibility = View.GONE
												Snackbar.make(binding.root, it.message, Snackbar.LENGTH_LONG)
														.setActionTextColor(Color.RED)
														.show()
										}
										is LaunchPadViewModel.UpdateState.Success -> {
												binding.loadingDots.visibility = View.GONE
												try {
														launchPadViewModel.updateOptionToFireBase(3)
														FirebaseDatabase.getInstance().reference.child("resetLaunchpad")
																.setValue(2)
														Snackbar.make(
																binding.root,
																"Cập hình vẽ thành công",
																Snackbar.LENGTH_LONG
														).show()
												} catch (e: Exception) {
														Snackbar.make(
																binding.root,
																"Có lỗi xảy ra khi cập nhật ~",
																Snackbar.LENGTH_LONG
														)
																.setActionTextColor(Color.RED)
																.show()
												}
										}
										else -> {

										}
								}
						}
				}
		}

		private fun openDialogSessionName() {
				dialog = Dialog(this)
				dialog.setContentView(R.layout.layout_dialog_session_launch_pad)
				dialog.setCanceledOnTouchOutside(false)
				dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
				val edtSessionName = dialog.findViewById<TextInputEditText>(R.id.edtName)
				val btnOk = dialog.findViewById<Button>(R.id.btnOk)
				val btnCancel = dialog.findViewById<Button>(R.id.btnCancel)
				btnOk.setOnClickListener {
						if (edtSessionName.text.toString().trim().isNotEmpty()) {
								try {
										val name = edtSessionName.text.toString().trim()
										lifecycleScope.launch {
												val result = launchPadViewModel.getCheckIfLaunchPadExists(name)
												Log.d("TEST", "$result ")
												if (result == 0) {
														val pixelDataTable = PixelDataTable()
														pixelDataTable.id = name
														pixelDataTable.data = launchPadAdapter.listPixel
														launchPadViewModel.saveLaunchPad(pixelDataTable)
														Snackbar.make(binding.root, "Lưu thành công", Snackbar.LENGTH_LONG)
																.show()
														dialog.dismiss()
												} else if (result >= 1) {
														edtSessionName.error = "Tên này đã tồn tại !"
														edtSessionName.requestFocus()
												} else {
														Snackbar.make(
																binding.root,
																"Lưu không thành công",
																Snackbar.LENGTH_LONG
														)
																.setActionTextColor(Color.RED)
																.show()
												}
										}


								} catch (e: Exception) {
										Snackbar.make(binding.root, "Lưu không thành công", Snackbar.LENGTH_LONG)
												.setActionTextColor(Color.RED)
												.show()
								}
						} else {
								edtSessionName.error = "Không được để trống"
								edtSessionName.requestFocus()
						}
				}
				btnCancel.setOnClickListener {
						dialog.dismiss()
				}
				dialog.show()
		}

		private fun setUpViewModel() {
				firebaseService = FireBaseService(FirebaseDatabase.getInstance())
				firebaseRepository = FireBaseRepositoryImpl(firebaseService)
				localDataBase = LocalDataBase(this)
				localDataRepository = LocalDataRepositoryImpl(localDataBase.getDao())
				appRepository = AppRepositoryImpl(firebaseRepository, localDataRepository)
				updateLaunchPadUseCase = UpdateLaunchPadUseCase(appRepository)
				saveLaunchPadUseCase = SaveLaunchPadUseCase(appRepository)
				updateOptionUseCase = UpdateOptionUseCase(appRepository)
				getCheckIfLaunchPadExists = CheckIfLaunchPadExists(appRepository)
				launchPadViewModel = ViewModelProvider(
						this, LaunchPadViewModelFactory(
								application,
								updateLaunchPadUseCase,
								updateOptionUseCase,
								saveLaunchPadUseCase,
								getCheckIfLaunchPadExists = getCheckIfLaunchPadExists
						)
				).get(LaunchPadViewModel::class.java)
		}

		private fun openBottomSheetColor() {
				val colorSheetSingleTon = ColorSheetSingleTon.getInstance()
				colorSheetSingleTon.setListener(object : ColorSheetSingleTon.ColorSheetListener {
						override fun onColorSelected(color: Int) {
								myReceiver.colorSelected = "#" + Integer.toHexString(color)
						}
				})
				colorSheetSingleTon.createColorSheet(resources.getIntArray(R.array.ColorSource))
				colorSheetSingleTon.show(supportFragmentManager)
		}

		override fun onBackPressed() {
				super.onBackPressed()
				val intent = Intent(ACTION_RESET_LAUNCHPAD)
				sendBroadcast(intent)
		}

		private fun setup() {
				binding.recyclerView.layoutManager = GridLayoutManager(this, column)
				binding.recyclerView.viewTreeObserver
						.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
								override fun onPreDraw(): Boolean {
										Log.d("TAG", "onPreDraw: onPreDraw")
										if (binding.recyclerView.viewTreeObserver.isAlive)
												binding.recyclerView.viewTreeObserver.removeOnPreDrawListener(this)
										val width = binding.recyclerView.width.toFloat() / column
										val height =
												(binding.recyclerView.height.toFloat() - binding.lyControl.height + 70) /
															row
										intent?.let {
												val data = intent.getParcelableExtra<PixelDataTable>("data")
												Log.d("TAG", "onPreDraw: $data")
												if(data==null){
														listPixel = getListInjectData(column, row)
												}
												else{
														listPixel = data.data
												}
										}
										launchPadAdapter = LaunchPadAdapter(height.toInt())
										binding.recyclerView.adapter = launchPadAdapter
										launchPadAdapter.setData(listPixel)
										myReceiver =
												MyReceiver(listPixel, launchPadAdapter, colorSelected, colorUnSelected)
										val touchListener =
												DragSelectTouchListener.create(this@LaunchPadActivity, myReceiver) {
														disableAutoScroll()
														mode = Mode.PATH
												}
										binding.recyclerView.addOnItemTouchListener(touchListener)
										launchPadAdapter.setOnLongClickListener {
												myReceiver.isAlreadyDraw = listPixel[it].isBrush == true
												touchListener.setIsActive(true, it)
										}
										return true
								}
						})
		}

		class MyReceiver(
				private var selectedItems: List<PixelData>,
				private val launchPadAdapter: LaunchPadAdapter,
				var colorSelected: String,
				private val colorUnselected: String,
		) : DragSelectReceiver {
				var isAlreadyDraw = false
				override fun getItemCount(): Int {
						return selectedItems.filter { it.isBrush!! }.size
				}

				override fun isIndexSelectable(index: Int): Boolean {
						return true
				}

				override fun isSelected(index: Int): Boolean {
						return false
				}

				override fun setSelected(index: Int, selected: Boolean) {
						if (selected) {
								Log.d("TAG", "setSelected: $isAlreadyDraw")
								if (!isAlreadyDraw) {
										val holder = launchPadAdapter.getViewHolderAtPosition(index)
										if (holder != null) {
												launchPadAdapter.setColorForPixel(holder, colorSelected)
										}
										selectedItems[index].color = colorSelected
										selectedItems[index].isBrush = true
										launchPadAdapter.setData(selectedItems)
								} else {
										val holder = launchPadAdapter.getViewHolderAtPosition(index)
										if (holder != null) {
												launchPadAdapter.setColorForPixel(holder, colorUnselected)
										}
										selectedItems[index].color = colorUnselected
										selectedItems[index].isBrush = false
										launchPadAdapter.setData(selectedItems)
								}
						}
				}
		}
}

fun getListInjectData(column: Int, row: Int): List<PixelData> {
		val list = mutableListOf<PixelData>()
		for (i in 0 until column * row) list.add(PixelData())
		return list
}