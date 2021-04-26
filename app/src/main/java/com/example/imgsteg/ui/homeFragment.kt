package com.example.imgsteg.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.imgsteg.databinding.FragmentEncodeBinding
import com.example.imgsteg.databinding.FragmentHomeBinding

class homeFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val binding = FragmentHomeBinding.inflate(inflater)

        binding.encButton.setOnClickListener {
           findNavController().navigate(homeFragmentDirections.actionHomeFragmentToEncodeFragment())
        }

        binding.decBtn.setOnClickListener {
            findNavController().navigate(homeFragmentDirections.actionHomeFragmentToDecodeFragment())
        }

        return binding.root
    }
}