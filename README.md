# Chatbot FX
A chatbot controlling ollama for LLM text2text, piper for text2speech and webui_forge for text2image.
It is written in Java. The Ui Framework is JavaFX. 
It is accessing
* ollama by ollama-4j
* piper by starting processes and raw Output
* webui_forge by REST API

You need the following preparation:
* Install Ollama (https://ollama.com/).
  * Create a directory for the llm_models 
  * Download LLM models ending with ".gguf" to this directory 
  * (e.g. https://huggingface.co/bartowski/Llama-3.1-8B-Lexi-Uncensored-V2-GGUF/resolve/main/Llama-3.1-8B-Lexi-Uncensored-V2-Q8_0.gguf)
* Install Piper (https://github.com/rhasspy/piper/releases/tag/2023.11.14-2 or a later version)
  * Create a directory for the text 2 speach models 
  * Download text-2-speach models ending with ".onnx" (together with it's ".onnx.json" configuration and its model card) to that directory
  * (e.g. https://huggingface.co/csukuangfj/vits-piper-en_GB-cori-high/tree/main)
  * Rename the 3 files to the schema like in this example
    * en_GB-cori-high.onnx (the model)
    * en_GB_alba-medium_MODEL_CARD (the model card) - only text based description about the model
    * en_GB_alba-medium.onnx.json (the config file)
* Install stable diffusion webui forge  (https://github.com/lllyasviel/stable-diffusion-webui-forge/releases/tag/latest)
  * Run it
  * Download text 2 image models ending with ".safetensors" to the installed modules directory (webui_forge_cu121_torch231\webui\models\Stable-diffusion)
  * (e.g. https://civitai.com/api/download/models/143906?type=Model&format=SafeTensor&size=pruned&fp=fp16)
  * change the following line to the file webui_forge_cu121_torch231\webui\webui-user.bat 
    * set COMMANDLINE_ARGS=--xformers --autolaunch --listen --api --medvram --cuda-malloc
  * start webui forge by calling webui_forge_cu121_torch231\run.bat
* Create a directory where you put in the model cards of the chatbot
  * These contain the information about the used 
    * LLM Model
    * The basic parameters for the LLM Model
    * The basic System Prompt for the LLM Model
    * Text 2 Speech Model
    * Text 2 Image Model
  * e.g. ParryHotter.json:
   {
    "modelCardName" : "PerryHotter",
    "llmModel" : "Llama-3.1-8B-Lexi-Uncensored-V2-Q8_0",
    "temperature" : 1.7,
    "top_p" : 0.5,
    "top_k" : 50,
    "system" : "You are Perry Hotter. A young wizard in the wizard school of Warthogs. You are friendly.",
    "txt2ImgModel" : "epicrealism_naturalSinRC1VAE_SD15",
    "txt2ImgModelStyle" : "",
    "ttsModel" : "en_GB-cori-high"
    }
  * Also you can add a image file (.png) of the dimensions 528x768 pixels with the same base file name like the model name.
* Now start the chatbot
  * A window will start up, where all the dictionaries, mentioned above are, must be set.
  * It is also tested, that Ollama and webui-forge is up and running

  

