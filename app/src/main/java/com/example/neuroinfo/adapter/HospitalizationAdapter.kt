package com.example.neuroinfo.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.neuroinfo.R
import com.example.neuroinfo.model.Hospitalization

// Адаптер - это "переходник" между данными (списком) и экраном (RecyclerView).
// Он знает, как превратить объект Hospitalization в красивую плашку на экране.
class HospitalizationAdapter(
    // var items - список данных, который мы будем показывать.
    private var items: List<Hospitalization>,
    // (Hospitalization) -> Unit - это тип функции-колбэка.
    // Она принимает Hospitalization и ничего не возвращает (Unit == void).
    // Мы вызываем её, когда пользователь нажимает на элемент.
    private val onCallClicked: (Hospitalization) -> Unit
) : RecyclerView.Adapter<HospitalizationAdapter.ViewHolder>() {

    // ViewHolder (Держатель Вида) - оптимизация.
    // Он находит ссылки на элементы (текстовые поля) один раз и хранит их,
    // чтобы не искать их снова при каждой прокрутке (это дорого для процессора).
    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val name: TextView = itemView.findViewById(R.id.patientName)
        val status: TextView = itemView.findViewById(R.id.status)
    }

    // Создает новый "вагончик" (View) для списка. Вызывается, когда на экране нужно нарисовать новый элемент,
    // а старых, которые можно переиспользовать, еще нет.
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        // LayoutInflater берет XML-файл (item_hospitalization) и превращает его в настоящие объекты View в памяти.
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_hospitalization, parent, false)
        return ViewHolder(view)
    }

    // Самый главный метод. "Биндит" (привязывает) данные к View.
    // Вызывается постоянно, когда ты скроллишь список.
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position] // Берем конкретного пациента по номеру позиции
        
        // Заполняем поля данными из объекта
        holder.name.text = item.patientName
        holder.status.text = item.status

        // Вешаем слушатель клика на ВЕСЬ элемент списка (itemView).
        holder.itemView.setOnClickListener {
            // Вызываем ту самую функцию, которую нам передали из MainActivity.
            // Мы как бы говорим Activity: "Эй, нажали вот на этого парня!"
            onCallClicked(item)
        }
    }

    // Просто говорит списку, сколько у нас всего элементов.
    // => стрелочная функция, сокращение для { return items.size }
    override fun getItemCount() = items.size

    // Наш кастомный метод.
    // Когда данные меняются (например, фильтрация), мы подменяем список и просим перерисовать.
    fun updateList(newList: List<Hospitalization>) {
        items = newList
        // notifyDataSetChanged - "ядерная кнопка". Говорит списку: "Всё изменилось, перерисуй всё заново!".
        // Для простых списков ок, для сложных используют DiffUtil (но это потом).
        notifyDataSetChanged()
    }
}